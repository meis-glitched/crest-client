package com.crest.client.catstrokes;

import dev.bongocat.client.live2d.NativeCat;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Comparator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU compositor for the Live2D Bango Cat model. Renders the model drawables
 * through a private core-profile GL shader into a 612×354 FBO, composites
 * overlays on top, and returns the read-back rows re-ordered top-to-bottom.
 *
 * <p>All GL calls use raw LWJGL (no {@code GlStateManager}, no
 * {@code DynamicTexture}). This class is designed to be called exclusively from
 * a single dedicated compositor thread that owns its own GLFW/OpenGL context,
 * keeping all raw GL completely off the main render thread.</p>
 */
public final class CatRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Crest/CatStrokes");

    public static final int W = 612;
    public static final int H = 354;

    private static final int N_KEYS = 15;

    private int prog;
    private int uMatrixLoc;
    private int uColorLoc;
    private int uTexLoc;
    private int fbo;
    private int fboTex;
    private int vao;
    private int vbo;
    private int ebo;

    private final int[] texGpu = new int[2];

    private byte[] backdropPngBytes;
    private byte[] leftUpPngBytes;
    private byte[][] keyPngBytes;
    private byte[][] handPngBytes;
    private int bgTex;
    private int leftUpTex;
    private final int[] keyTex = new int[N_KEYS];
    private final int[] handTex = new int[N_KEYS + 1];
    private boolean overlaysUploaded;

    private int activeKeyIdx = -1;
    private boolean backdropVisible = true;
    private boolean glInited;
    private boolean oneTimeLogDone;
    private boolean fboRestoreLogged;

    private final ByteBuffer readBuf = BufferUtils.createByteBuffer(W * H * 4);
    private final ByteBuffer flipBuf = BufferUtils.createByteBuffer(W * H * 4);

    private final int[] meta = new int[512];
    private final float[] pos = new float[65536];
    private final float[] uv = new float[65536];
    private final char[] idx = new char[131072];
    private final float[] verts = new float[65536 * 4];
    private final short[] idxS = new short[131072];
    private final Integer[] order = new Integer[64];

    public CatRenderer() {}

    public void ensureGl() {
        if (glInited) return;
        setupGl();
        glInited = true;
    }

    public boolean isTextureLoaded() {
        return texGpu[0] != 0;
    }

    public boolean isRightHandTextureLoaded() {
        return texGpu[1] != 0;
    }

    public boolean isOverlaysUploaded() {
        return overlaysUploaded;
    }

    private void setupGl() {
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ebo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL30.glBindVertexArray(0);

        prog = compileProgram();
        uMatrixLoc = GL20.glGetUniformLocation(prog, "u_matrix");
        uColorLoc = GL20.glGetUniformLocation(prog, "u_baseColor");
        uTexLoc = GL20.glGetUniformLocation(prog, "u_tex");

        fboTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, W, H, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, fboTex, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static int compileProgram() {
        String vs = "#version 330 core\n"
                + "in vec2 a_pos; in vec2 a_uv;\n"
                + "uniform mat4 u_matrix;\n"
                + "out vec2 v_uv;\n"
                + "void main(){ gl_Position = u_matrix * vec4(a_pos, 0.0, 1.0); v_uv = a_uv; }\n";
        String fs = "#version 330 core\n"
                + "in vec2 v_uv;\n"
                + "uniform sampler2D u_tex;\n"
                + "uniform vec4 u_baseColor;\n"
                + "out vec4 o;\n"
                + "void main(){\n"
                + "  vec2 fuv = vec2(v_uv.x, 1.0 - v_uv.y);\n"
                + "  vec4 c = texture(u_tex, fuv) * u_baseColor;\n"
                + "  c.rgb *= c.a;\n"
                + "  o = c;\n"
                + "}\n";
        int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(v, vs);
        GL20.glCompileShader(v);
        if (GL20.glGetShaderi(v, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("bongocat vs: " + GL20.glGetShaderInfoLog(v));
        }
        int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(f, fs);
        GL20.glCompileShader(f);
        if (GL20.glGetShaderi(f, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("bongocat fs: " + GL20.glGetShaderInfoLog(f));
        }
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("bongocat link: " + GL20.glGetProgramInfoLog(p));
        }
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return p;
    }

    public void loadModelTexture(byte[] png) {
        texGpu[0] = uploadRawTexture(png, "bongocat-model");
    }

    public void loadModelTextureRight(byte[] png) {
        texGpu[1] = uploadRawTexture(png, "bongocat-righthand");
    }

    private int uploadRawTexture(byte[] png, String label) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer ch = stack.mallocInt(1);
            ByteBuffer src = BufferUtils.createByteBuffer(png.length);
            src.put(png).flip();
            ByteBuffer img = STBImage.stbi_load_from_memory(src, w, h, ch, 4);
            if (img == null) {
                throw new IllegalStateException("bongocat stbi_load failed for " + label
                        + ": " + STBImage.stbi_failure_reason());
            }
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w.get(0), h.get(0), 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, img);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL13.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL13.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            STBImage.stbi_image_free(img);
            LOGGER.info("[BongoCat] uploaded raw texture {} ({}x{})", label, w.get(0), h.get(0));
            return tex;
        }
    }

    public void storeOverlays(byte[] bg, byte[][] keys, byte[] leftUp, byte[][] hands) {
        this.backdropPngBytes = bg;
        this.leftUpPngBytes = leftUp;
        this.keyPngBytes = keys;
        this.handPngBytes = hands;
    }

    public void uploadOverlays() {
        if (overlaysUploaded) return;
        if (backdropPngBytes != null) {
            bgTex = uploadRawTexture(backdropPngBytes, "bongocat-bg");
        }
        if (leftUpPngBytes != null) {
            leftUpTex = uploadRawTexture(leftUpPngBytes, "bongocat-hand-up");
            handTex[N_KEYS] = leftUpTex;
        }
        for (int i = 0; i < N_KEYS; i++) {
            if (keyPngBytes != null && keyPngBytes[i] != null) {
                keyTex[i] = uploadRawTexture(keyPngBytes[i], "bongocat-key-" + i);
            }
            if (handPngBytes != null && handPngBytes[i] != null) {
                handTex[i] = uploadRawTexture(handPngBytes[i], "bongocat-hand-" + i);
            }
        }
        overlaysUploaded = true;
        LOGGER.info("[BongoCat] all overlay textures uploaded ({}/{}/{}={})",
                bgTex != 0, leftUpTex != 0, N_KEYS,
                Arrays.stream(keyTex).filter(t -> t != 0).count());
    }

    public void setActiveKey(int idx) {
        activeKeyIdx = idx;
    }

    public void setBackdropVisible(boolean visible) {
        backdropVisible = visible;
    }

    public ByteBuffer render(long mainHandle, long rightHandle, float canvasW, float canvasH) {
        ensureGl();
        if (texGpu[0] == 0) {
            throw new IllegalStateException("bongocat model texture not loaded");
        }
        if (!oneTimeLogDone) {
            oneTimeLogDone = true;
            LOGGER.info("[BongoCat] renderer FBO {}/{} tex {} drawables={}",
                    W, H, texGpu[0], NativeCat.nativeGetDrawableCount(mainHandle));
        }

        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevWrite = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, W, H);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL20.glUseProgram(prog);
        GL20.glUniform1i(uTexLoc, 0);
        float scale = H / canvasH;

        float[] modelMvp = new float[16];
        modelMvp[0] = 2f * scale / W;
        modelMvp[5] = 2f * scale / H;
        modelMvp[10] = 1f;
        modelMvp[15] = 1f;
        float[] overlayMvp = modelMvp.clone();
        overlayMvp[12] = -1f;
        overlayMvp[13] = -1f;

        GL11.glEnable(GL11.GL_BLEND);
        GL30.glBindVertexArray(vao);

        if (overlaysUploaded && backdropVisible && bgTex != 0) {
            drawOverlay(bgTex, 1f, canvasW, canvasH, overlayMvp);
        }

        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawModelPass(mainHandle, modelMvp, 0);

        if (rightHandle != 0 && texGpu[1] != 0) {
            drawModelPass(rightHandle, modelMvp, 1);
        }

        GL20.glUniformMatrix4fv(uMatrixLoc, false, overlayMvp);
        if (overlaysUploaded) {
            if (activeKeyIdx >= 0 && activeKeyIdx < N_KEYS) {
                if (keyTex[activeKeyIdx] != 0) {
                    drawOverlay(keyTex[activeKeyIdx], 1f, canvasW, canvasH, overlayMvp);
                }
                if (handTex[activeKeyIdx] != 0) {
                    drawOverlay(handTex[activeKeyIdx], 1f, canvasW, canvasH, overlayMvp);
                }
            } else if (handTex[N_KEYS] != 0) {
                drawOverlay(handTex[N_KEYS], 1f, canvasW, canvasH, overlayMvp);
            }
        }

        readBuf.clear();
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuf);
        readBuf.flip();
        flipBuf.position(0);
        long srcAddr = MemoryUtil.memAddress(readBuf);
        long dstAddr = MemoryUtil.memAddress(flipBuf);
        for (int y = 0; y < H; y++) {
            MemoryUtil.memCopy(srcAddr + (long) (H - 1 - y) * W * 4,
                    dstAddr + (long) y * W * 4, (long) W * 4);
        }
        flipBuf.position(0);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevWrite);
        GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (!fboRestoreLogged) {
            fboRestoreLogged = true;
            int afterRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int afterWrite = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            LOGGER.info("[BongoCat] fbo check: saved read={} write={} restored-read={} restored-write={}",
                    prevRead, prevWrite, afterRead, afterWrite);
        }
        if (blendWasOn) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (depthWasOn) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL20.glUseProgram(0);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return flipBuf;
    }

    private void drawModelPass(long handle, float[] mvp, int texSlot) {
        int nd = NativeCat.nativeGetDrawableCount(handle);
        NativeCat.nativeRead(handle, meta, pos, uv, idx);

        int totalV = 0, totalI = 0;
        for (int i = 0; i < nd; i++) {
            totalV += meta[i * 7 + 0];
            totalI += meta[i * 7 + 1];
        }

        int baseV = 0;
        int ci = 0;
        for (int i = 0; i < nd; i++) {
            int vc = meta[i * 7 + 0];
            int vi = 0;
            for (int j = 0; j < i; j++) {
                vi += meta[j * 7 + 0];
            }
            for (int k = 0; k < vc; k++) {
                verts[(baseV + k) * 4 + 0] = pos[(vi + k) * 2 + 0];
                verts[(baseV + k) * 4 + 1] = pos[(vi + k) * 2 + 1];
                verts[(baseV + k) * 4 + 2] = uv[(vi + k) * 2 + 0];
                verts[(baseV + k) * 4 + 3] = uv[(vi + k) * 2 + 1];
            }
            for (int k = 0; k < meta[i * 7 + 1]; k++) {
                idxS[ci + k] = (short) (idx[cumIndex(i, meta) + k] + baseV);
            }
            ci += meta[i * 7 + 1];
            baseV += vc;
        }

        ByteBuffer vertBB = MemoryUtil.memAlloc(totalV * 4 * 4);
        vertBB.asFloatBuffer().put(verts, 0, totalV * 4).flip();
        ByteBuffer idxBB = MemoryUtil.memAlloc(totalI * 2);
        idxBB.asShortBuffer().put(idxS, 0, totalI).flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBB, GL15.GL_STREAM_DRAW);
        MemoryUtil.memFree(vertBB);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, idxBB, GL15.GL_STREAM_DRAW);
        MemoryUtil.memFree(idxBB);

        GL20.glUniformMatrix4fv(uMatrixLoc, false, mvp);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texGpu[texSlot]);

        for (int i = 0; i < nd; i++) {
            order[i] = i;
        }
        Arrays.sort(order, 0, nd, Comparator.comparingInt(o -> meta[o * 7 + 6]));

        for (int oi = 0; oi < nd; oi++) {
            int i = order[oi];
            int ic = meta[i * 7 + 1];
            float op = meta[i * 7 + 2] / 1000f;
            int visible = meta[i * 7 + 3];
            int blend = meta[i * 7 + 4];
            if (ic == 0 || visible == 0) {
                continue;
            }
            switch (blend) {
                case 1:
                    GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE,
                            GL11.GL_ZERO, GL11.GL_ONE);
                    break;
                case 2:
                    GL14.glBlendFuncSeparate(GL30.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ZERO, GL11.GL_ONE);
                    break;
                default:
                    GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    break;
            }
            GL20.glUniform4f(uColorLoc, 1f, 1f, 1f, op);
            GL11.glDrawElements(GL11.GL_TRIANGLES, ic, GL11.GL_UNSIGNED_SHORT,
                    (long) cumIndex(i, meta) * 2);
        }
    }

    private static int cumIndex(int i, int[] meta) {
        int c = 0;
        for (int j = 0; j < i; j++) {
            c += meta[j * 7 + 1];
        }
        return c;
    }

    private void drawOverlay(int tex, float alpha, float canvasW, float canvasH, float[] mvp) {
        GL20.glUniformMatrix4fv(uMatrixLoc, false, mvp);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUniform4f(uColorLoc, 1f, 1f, 1f, alpha);
        drawOverlayQuad(canvasW, canvasH);
    }

    private void drawOverlayQuad(float canvasW, float canvasH) {
        float[] qv = {
                0f, 0f, 0f, 0f,
                canvasW, 0f, 1f, 0f,
                canvasW, canvasH, 1f, 1f,
                0f, canvasH, 0f, 1f
        };
        short[] qi = {0, 1, 2, 0, 2, 3};
        ByteBuffer qvB = MemoryUtil.memAlloc(qv.length * 4);
        qvB.asFloatBuffer().put(qv).flip();
        ByteBuffer qiB = MemoryUtil.memAlloc(qi.length * 2);
        qiB.asShortBuffer().put(qi).flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, qvB, GL15.GL_STREAM_DRAW);
        MemoryUtil.memFree(qvB);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, qiB, GL15.GL_STREAM_DRAW);
        MemoryUtil.memFree(qiB);
        GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_SHORT, 0);
    }
}
