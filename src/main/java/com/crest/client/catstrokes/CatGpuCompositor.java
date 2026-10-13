package com.crest.client.catstrokes;

import dev.bongocat.client.live2d.NativeCat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Live2D Bongo Cat GL rendering on a background thread with its own
 * GLFW/OpenGL context, keeping all raw GL operations completely off the main
 * render thread. The main thread only does {@code DynamicTexture.upload()} +
 * {@code graphics.blit()} — operations already proven safe on radeonsi.
 *
 * <h2>Thread model</h2>
 * <ul>
 *   <li><b>Main (render) thread</b>: calls {@link #updateSnapshot} to publish
 *       param values and {@link #getLatestFrame} to consume the most recently
 *       composited frame (a premultiplied RGBA ByteBuffer).</li>
 *   <li><b>Compositor thread</b>: owns a hidden GLFW window and its own GL
 *       context. Reads the latest snapshot, applies Live2D params via JNI,
 *       calls {@link CatRenderer#render}, and publishes the result into a
 *       double-buffered slot.</li>
 * </ul>
 *
 * <h2>Synchronisation</h2>
 * Both directions use a single volatile object reference (snapshot in, frame
 * out). The double-buffer swap for frame output is lock-free: the compositor
 * always writes to the slot the main thread is <em>not</em> reading, and the
 * volatile write of {@code readySlot} publishes the new data with
 * happens-before semantics.</p>
 */
public final class CatGpuCompositor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Crest/CatStrokes");

    private static final int W = CatRenderer.W;
    private static final int H = CatRenderer.H;

    // ── Config (set before start, immutable after) ─────────────────────────
    private long mainHandle;
    private long rightHandle;
    private float canvasW;
    private float canvasH;
    private float[] paramMinMax;
    private int paramCount;
    private byte[] texturePng;
    private byte[] texturePngRight;
    private byte[] backdropPng;
    private byte[][] keyPngs;
    private byte[] leftUpPng;
    private byte[][] handPngs;

    // ── Input snapshot (main → compositor, volatile) ───────────────────────
    private static final class Snapshot {
        final float[] params;
        final float dt;
        final int activeKey;
        final boolean backdropVisible;

        Snapshot(float[] p, float dt, int ak, boolean bv) {
            this.params = p;
            this.dt = dt;
            this.activeKey = ak;
            this.backdropVisible = bv;
        }
    }

    private volatile Snapshot latestSnapshot;

    // ── Frame output (compositor → main, double-buffer) ────────────────────
    private final ByteBuffer slotA = BufferUtils.createByteBuffer(W * H * 4);
    private final ByteBuffer slotB = BufferUtils.createByteBuffer(W * H * 4);
    private volatile int readySlot = -1;

    // ── Thread state ───────────────────────────────────────────────────────
    private Thread thread;
    private long windowHandle;
    private volatile boolean failed;
    private volatile boolean ready;

    // ── Renderer (created and owned by compositor thread) ──────────────────
    private CatRenderer renderer;

    public CatGpuCompositor() {}

    // ── Configuration (called from main thread before start) ───────────────

    public void configure(long mainHandle, long rightHandle,
                          float canvasW, float canvasH,
                          float[] paramMinMax, int paramCount,
                          byte[] texturePng, byte[] texturePngRight,
                          byte[] backdropPng, byte[][] keyPngs,
                          byte[] leftUpPng, byte[][] handPngs) {
        this.mainHandle = mainHandle;
        this.rightHandle = rightHandle;
        this.canvasW = canvasW;
        this.canvasH = canvasH;
        this.paramMinMax = paramMinMax;
        this.paramCount = paramCount;
        this.texturePng = texturePng;
        this.texturePngRight = texturePngRight;
        this.backdropPng = backdropPng;
        this.keyPngs = keyPngs;
        this.leftUpPng = leftUpPng;
        this.handPngs = handPngs;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void start() {
        if (thread != null) return;
        thread = new Thread(this::run, "crest-cat-compositor");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isReady() {
        return ready;
    }

    // ── Main-thread API ────────────────────────────────────────────────────

    /**
     * Publishes a fresh input snapshot. Called from the render thread once per
     * frame. The params array is copied so the caller can reuse it.
     */
    public void updateSnapshot(float[] params, float dt,
                               int activeKey, boolean backdropVisible) {
        float[] copy = Arrays.copyOf(params, Math.min(params.length, paramCount));
        latestSnapshot = new Snapshot(copy, dt, activeKey, backdropVisible);
    }

    /**
     * Returns the most recently composited frame (a premultiplied RGBA
     * {@link ByteBuffer}, rows top-to-bottom) or {@code null} if no frame has
     * been published yet. Safe to call from the render thread: the returned
     * buffer is the double-buffer slot the compositor is currently <em>not</em>
     * writing, so it will not change while this frame is being consumed.
     */
    public ByteBuffer getLatestFrame() {
        int idx = readySlot;
        if (idx < 0) {
            return null;
        }
        ByteBuffer src = (idx == 0) ? slotA : slotB;
        src.position(0);
        return src;
    }

    /**
     * Blocks until the compositor has published its first frame, the compositor
     * has failed, or {@code timeoutMillis} elapses. Used by the stasis
     * diagnostic path so the one-shot composition is available before the first
     * {@link #getLatestFrame()} call.
     *
     * @return {@code true} if a frame became available
     */
    public boolean waitForFirstFrame(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!failed && readySlot < 0) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return readySlot >= 0;
    }

    // ── Compositor thread ──────────────────────────────────────────────────

    private void run() {
        try {
            CatDiag.log("compositor: thread starting");
            if (!GLFW.glfwInit()) {
                CatDiag.log("compositor: glfwInit failed");
                LOGGER.error("[BongoCat] compositor: glfwInit failed");
                failed = true;
                return;
            }
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);

            windowHandle = GLFW.glfwCreateWindow(2, 2, "crest-cat-compositor", 0, 0);
            if (windowHandle == 0) {
                CatDiag.log("compositor: failed to create GLFW window");
                LOGGER.error("[BongoCat] compositor: failed to create GLFW window");
                failed = true;
                return;
            }

            GLFW.glfwMakeContextCurrent(windowHandle);
            GL.createCapabilities();

            LOGGER.info("[BongoCat] compositor: GL context — vendor={} renderer={} version={}",
                    GL11.glGetString(GL11.GL_VENDOR),
                    GL11.glGetString(GL11.GL_RENDERER),
                    GL11.glGetString(GL11.GL_VERSION));
            CatDiag.log("compositor: GL context vendor=" + GL11.glGetString(GL11.GL_VENDOR)
                    + " renderer=" + GL11.glGetString(GL11.GL_RENDERER)
                    + " version=" + GL11.glGetString(GL11.GL_VERSION));

            renderer = new CatRenderer();
            renderer.ensureGl();

            if (texturePng != null) {
                renderer.loadModelTexture(texturePng);
            }
            if (texturePngRight != null && rightHandle != 0) {
                renderer.loadModelTextureRight(texturePngRight);
            }
            renderer.storeOverlays(backdropPng, keyPngs, leftUpPng, handPngs);
            renderer.uploadOverlays();

            ready = true;
            LOGGER.info("[BongoCat] compositor: ready canvas={}x{} params={} rh={}",
                    canvasW, canvasH, paramCount, rightHandle != 0);
            CatDiag.log("compositor: READY canvas=" + canvasW + "x" + canvasH
                    + " params=" + paramCount + " rh=" + (rightHandle != 0)
                    + " texLoaded=" + renderer.isTextureLoaded()
                    + " overlays=" + renderer.isOverlaysUploaded());

            long frames = 0;
            long dtMs = 0;
            while (!Thread.currentThread().isInterrupted() && !failed) {
                Snapshot snap = latestSnapshot;
                if (snap == null) {
                    Thread.sleep(5);
                    continue;
                }

                long t0 = System.nanoTime();
                renderer.setActiveKey(snap.activeKey);
                renderer.setBackdropVisible(snap.backdropVisible);

                float[] params = snap.params;
                for (int i = 0; i < Math.min(params.length, paramCount); i++) {
                    float min = paramMinMax[i * 3];
                    float max = paramMinMax[i * 3 + 1];
                    float v = params[i];
                    if (v < min) v = min;
                    if (v > max) v = max;
                    NativeCat.nativeSetParam(mainHandle, i, v);
                    if (rightHandle != 0) {
                        NativeCat.nativeSetParam(rightHandle, i, v);
                    }
                }

                NativeCat.nativeUpdate(mainHandle, snap.dt);
                if (rightHandle != 0) {
                    NativeCat.nativeUpdate(rightHandle, snap.dt);
                }

                ByteBuffer result = renderer.render(mainHandle, rightHandle, canvasW, canvasH);

                int nextSlot = (readySlot == 0) ? 1 : 0;
                ByteBuffer dest = (nextSlot == 0) ? slotA : slotB;
                dest.clear();
                result.position(0);
                dest.put(result);
                dest.flip();
                readySlot = nextSlot;

                dtMs += (System.nanoTime() - t0) / 1000000;
                GLFW.glfwPollEvents();

                if (++frames % 120 == 0) {
                    CatDiag.log(String.format(
                            "compositor: ALIVE frames=%d avgRenderMs=%.2f snapDt=%.4f p0=%.2f key=%d",
                            frames, (double) dtMs / 120, snap.dt, snap.params[0], snap.activeKey));
                    dtMs = 0;
                }

                // Pace: render at most once per distinct snapshot. Prevents the
                // double-buffer slot from being overwritten while the main thread
                // is still consuming the previous publish.
                while (latestSnapshot == snap && !failed) {
                    Thread.sleep(1);
                }
            }

            CatDiag.log("compositor: thread exiting normal");
            LOGGER.info("[BongoCat] compositor: thread exiting");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CatDiag.log("compositor: interrupted");
        } catch (Throwable t) {
            CatDiag.log(t, "compositor: FATAL error");
            LOGGER.error("[BongoCat] compositor: fatal error", t);
            failed = true;
        } finally {
            if (windowHandle != 0) {
                GLFW.glfwDestroyWindow(windowHandle);
            }
        }
    }
}
