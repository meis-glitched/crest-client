package com.crest.client.catstrokes;

import com.crest.client.core.HudModule;
import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.profile.HudProfileManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bongocat.client.live2d.NativeCat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the two Live2D Bango Cat models (main cat + mouse-tracking right hand).
 * <p>
 * The {@link CatGpuCompositor} runs all raw GL on a dedicated background thread
 * with its own GLFW/OpenGL context. Each frame the main render thread computes
 * an input {@link CatGpuCompositor.Snapshot} (via {@code applyInputs}),
 * publishes it with {@code compositor.updateSnapshot()}, then pulls the latest
 * composited frame with {@code compositor.consumeFrame()} and uploads+blits it
 * as a {@link DynamicTexture} — the same proven-safe pipeline the standalone
 * bongocat-mod uses, keeping all raw GL completely off the main thread.
 */
public final class CatHud {
    private static final Logger LOGGER = LoggerFactory.getLogger("Crest/CatStrokes");

    private static final String MOC_PATH = "assets/bongocat/standard/model/cat/cat ori.moc3";
    private static final String TEX_PATH = "assets/bongocat/standard/model/cat/cat ori.512/texture_00.png";
    private static final String MOC_RH_PATH = "assets/bongocat/standard/model/cat right hand/cat ori right hand.moc3";
    private static final String TEX_RH_PATH = "assets/bongocat/standard/model/cat right hand/cat ori right hand.512/texture_00.png";

    private static final boolean STASIS =
            Boolean.getBoolean("cat.stasis") || "1".equals(System.getenv("CAT_STASIS"));

    private static CatHud instance;

    private long handle;
    private long rightHandle;

    private float canvasW = 1f;
    private float canvasH = 1f;
    private String[] paramIds;
    private float[] mmd;

    private int pMouseX = -1, pMouseY = -1;
    private int pLeftDown = -1, pRightDown = -1;
    private int pKeyDown = -1;
    private int pEyeL = -1, pEyeR = -1;
    private int pBreath = -1;
    private int pEyeBallX = -1, pEyeBallY = -1;
    private int pMouthOpen = -1;
    private int pBrowLY = -1, pBrowRY = -1;
    private int pBrowLForm = -1, pBrowRForm = -1;
    private int pBrowLAngle = -1, pBrowRAngle = -1;
    private int pBodyAngleX = -1, pBodyAngleY = -1, pBodyAngleZ = -1;
    private int pAngleX = -1, pAngleY = -1, pAngleZ = -1;
    private int pHairFront = -1, pHairSide = -1, pHairBack = -1;
    private int pEyeLSmile = -1, pEyeRSmile = -1;
    private int pMouthForm = -1;
    private int pCheek = -1;

    private float mouthOpen;
    private int activeKey = -1;
    private boolean[] keyPrev = new boolean[MAPPED_KEYS.length];
    private double[] keyLast = new double[MAPPED_KEYS.length];

    private DynamicTexture catDyn;
    private NativeImage catFrame;
    private byte[] texturePng;
    private byte[] texturePngRight;

    private final CatGpuCompositor compositor = new CatGpuCompositor();
    private float[] paramValues;

    private BlinkState blink = new BlinkState();
    private double lifeSeconds;

    private boolean failed;
    private boolean loaded;
    private int frameCounter;
    private boolean debugDumped;
    private int storedX, storedY, storedW, storedH;
    private boolean rectLogged;
    private static boolean screenDumped;
    private float opacityMul = 1f;

    private float prevMouseX;
    private float prevMouseY;
    private double mouseStillSince;
    private boolean mouseMoving;
    private float wanderX;
    private float wanderY;
    private final ExpressionController expression = new ExpressionController();
    private double lastActivity;
    private double[] clickEvents = new double[16];
    private int clickPos;
    private boolean prevLeftDown;
    private boolean prevRightDown;

    private CatHud() {}

    public static CatHud instance() {
        if (instance == null) {
            instance = new CatHud();
        }
        return instance;
    }

    public synchronized void init() {
        if (loaded || failed) {
            return;
        }
        CatDiag.log("CatHud.init() BEGIN stasis=" + STASIS);
        try {
            CatNatives.ensureLoaded();
            if (CatNatives.isFailed()) {
                LOGGER.warn("[BongoCat] natives unavailable; disabling cat overlay");
                CatDiag.log("CatHud.init() FAIL natives unavailable");
                failed = true;
                return;
            }
            CatDiag.log("CatHud.init() natives loaded, creating main model");
            byte[] moc = readResource(MOC_PATH);
            texturePng = readResource(TEX_PATH);
            handle = NativeCat.nativeCreate(moc);
            if (handle == 0) {
                throw new IllegalStateException("nativeCreate returned 0");
            }
            canvasW = NativeCat.nativeGetCanvasWidth(handle);
            canvasH = NativeCat.nativeGetCanvasHeight(handle);
            NativeCat.nativeUpdate(handle, 1f / 60f);

            byte[] mocRh = readResource(MOC_RH_PATH);
            texturePngRight = readResource(TEX_RH_PATH);
            rightHandle = NativeCat.nativeCreate(mocRh);
            if (rightHandle == 0) {
                LOGGER.warn("[BongoCat] right-hand model failed to load, continuing without it");
            }

            paramIds = NativeCat.nativeGetParamIds(handle);
            mmd = NativeCat.nativeGetParamMinMaxDefault(handle);
            paramValues = new float[paramIds.length];

            pMouseX = findParam("ParamMouseX");
            pMouseY = findParam("ParamMouseY");
            pLeftDown = findParam("ParamMouseLeftDown");
            pRightDown = findParam("ParamMouseRightDown");
            pKeyDown = findParam("CatParamKeyDown");
            pEyeL = findParam("ParamEyeLOpen");
            pEyeR = findParam("ParamEyeROpen");
            pBreath = findParam("ParamBreath");
            pEyeBallX = findParam("ParamEyeBallX");
            pEyeBallY = findParam("ParamEyeBallY");
            pMouthOpen = findParam("ParamMouthOpenY");
            pBrowLY = findParam("ParamBrowLY");
            pBrowRY = findParam("ParamBrowRY");
            pBrowLForm = findParam("ParamBrowLForm");
            pBrowRForm = findParam("ParamBrowRForm");
            pBodyAngleZ = findParam("ParamBodyAngleZ");
            pAngleZ = findParam("ParamAngleZ");
            pMouthForm = findParam("ParamMouthForm");
            pCheek = findParam("ParamCheek");
            pAngleX = findParam("ParamAngleX");
            pAngleY = findParam("ParamAngleY");
            pBodyAngleX = findParam("ParamBodyAngleX");
            pBodyAngleY = findParam("ParamBodyAngleY");
            pHairFront = findParam("ParamHairFront");
            pHairSide = findParam("ParamHairSide");
            pHairBack = findParam("ParamHairBack");
            pEyeLSmile = findParam("ParamEyeLSmile");
            pEyeRSmile = findParam("ParamEyeRSmile");
            pBrowLAngle = findParam("ParamBrowLAngle");
            pBrowRAngle = findParam("ParamBrowRAngle");

            byte[] backdrop = readResource("assets/bongocat/standard/bg.png");
            byte[][] keys = new byte[15][];
            byte[][] hands = new byte[15][];
            for (int i = 0; i < 15; i++) {
                keys[i] = readResource("assets/bongocat/standard/keyboard/" + i + ".png");
                hands[i] = readResource("assets/bongocat/standard/lefthand/" + i + ".png");
            }
            byte[] leftUp = readResource("assets/bongocat/standard/lefthand/leftup.png");

            compositor.configure(
                    handle, rightHandle,
                    canvasW, canvasH,
                    mmd, paramIds.length,
                    texturePng, texturePngRight,
                    backdrop, keys, leftUp, hands
            );
            compositor.start();

            loaded = true;
            LOGGER.info(
                "[BongoCat] loaded pipeline=v4 compositor canvas={}x{} drawables={} params={} rh={}",
                canvasW, canvasH, NativeCat.nativeGetDrawableCount(handle), paramIds.length, rightHandle != 0
            );
            CatDiag.log("CatHud.init() DONE canvas=" + canvasW + "x" + canvasH
                    + " drawables=" + NativeCat.nativeGetDrawableCount(handle)
                    + " params=" + paramIds.length
                    + " rh=" + (rightHandle != 0) + " compositor started=" + (compositor != null));
        } catch (Exception e) {
            failed = true;
            CatDiag.log(e, "CatHud.init() EXCEPTION");
            LOGGER.error("[BongoCat] failed to initialize CatHud", e);
        }
    }

    /**
     * Called from {@code Gui.extractRenderState} @RETURN — after the entire GUI
     * state extraction is complete. Publishes an input snapshot to the
     * compositor, pulls the latest composited frame (if available), uploads it
     * to the {@link DynamicTexture}, and blits it at the stored rect.
     * No raw GL runs on the main thread.
     */
    public void extract(Minecraft mc, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!com.crest.client.core.CrestModules.isEnabled("bango_cat")) {
            return;
        }
        if (failed || !loaded) {
            return;
        }
        if (!isInGame(mc)) {
            mouseMoving = false;
            return;
        }
        int bw = storedW;
        int bh = storedH;
        if (bw <= 0 || bh <= 0) {
            return;
        }
        try {
            if (catDyn == null) {
                catFrame = new NativeImage(CatRenderer.W, CatRenderer.H, false);
                catDyn = new DynamicTexture(() -> "bongocat-cat", catFrame);
            }

            float dt = Math.min(deltaTracker.getRealtimeDeltaTicks() / 20.0f, 0.1f);
            lifeSeconds += dt;
            CatStrokesConfig config = CatStrokesConfig.getInstance();
            applyInputs(mc, dt, config.mouseSensitivity);

            HudProfile.ModuleSettings s = catSettings();
            opacityMul = s != null ? clamp(s.opacity, 0f, 1f) : 1f;

            compositor.updateSnapshot(paramValues, dt, activeKey, config.showBackdrop);

            ByteBuffer frame = compositor.getLatestFrame();
            if (frame != null) {
                fillFrame(frame);
                catDyn.upload();
            } else if (STASIS && frameCounter == 0) {
                compositor.waitForFirstFrame(2000);
                frame = compositor.getLatestFrame();
                if (frame != null) {
                    fillFrame(frame);
                    catDyn.upload();
                }
            }

            frameCounter++;
            if (frameCounter % 60 == 0 || frameCounter == 1) {
                CatDiag.log(String.format(
                        "CatHud.extract() frame=%d loaded=%s failed=%s compReady=%s compFailed=%s frameIn=%s activeKey=%d mouseX=%.3f mouth=%.3f px=%08x",
                        frameCounter, loaded, failed, compositor.isReady(), compositor.isFailed(),
                        frame != null, activeKey, getParam(pMouseX), getParam(pMouthOpen),
                        sampleChecksum()));
            }

            if (s != null && (s.backgroundEnabled || s.borderEnabled || s.shadowEnabled)) {
                HudModule.renderChrome(graphics, storedX, storedY, bw, bh, s);
            }

            graphics.blit(catDyn.getTextureView(), catDyn.getSampler(),
                    storedX, storedY, storedX + bw, storedY + bh, 0f, 1f, 0f, 1f);
        } catch (Exception e) {
            failed = true;
            CatDiag.log(e, "CatHud.extract() EXCEPTION");
            LOGGER.error("[BongoCat] error in extract; disabling cat overlay", e);
        }
    }

    /**
     * Called from the HUD render layer to store the rect for use by {@link #extract}.
     * No GPU work happens here — just records position.
     */
    public void storeRect(int x, int y, int w, int h) {
        storedX = x;
        storedY = y;
        storedW = w;
        storedH = h;
        if (!rectLogged) {
            rectLogged = true;
            Minecraft mc = Minecraft.getInstance();
            LOGGER.info("[BongoCat] rect gui=({},{}) size=({}x{}) window={}x{}",
                    x, y, w, h,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    /**
     * One-shot screen dump: reads the bound FBO (drawFbo) to a PNG so we can
     * SEE exactly what "two screens" looks like. Called from GameRenderer.render TAIL.
     */
    public static void maybeDumpScreen(Minecraft mc) {
        if (screenDumped || mc == null || mc.level == null) {
            return;
        }
        screenDumped = true;
        try {
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            if (w <= 0 || h <= 0) return;
            java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            buf.rewind();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = buf.get() & 0xFF;
                    int g = buf.get() & 0xFF;
                    int b = buf.get() & 0xFF;
                    int a = buf.get() & 0xFF;
                    img.setRGB(x, h - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            java.io.File dir = new java.io.File("/tmp/catstrokes");
            dir.mkdirs();
            javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, "screen_dump.png"));
            LOGGER.info("[BongoCat] screen dump saved: /tmp/catstrokes/screen_dump.png ({}x{})", w, h);
        } catch (Exception e) {
            LOGGER.warn("[BongoCat] screen dump failed", e);
        }
    }

    private boolean isInGame(Minecraft mc) {
        return mc.getWindow() != null && mc.level != null && !mc.options.hideGui &&
                (mc.screen == null || mc.screen instanceof com.crest.client.core.hud.editor.HudEditorV2);
    }

    /**
     * Computes param values into {@link #paramValues}. The compositor reads
     * these when publishing a snapshot. No native JNI calls happen here.
     */
    private void applyInputs(Minecraft mc, float dt, float sens) {
        double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float normX = (sw <= 0) ? 0f : (float) ((mx - sw / 2.0) / (sw / 2.0));
        float normY = (sh <= 0) ? 0f : (float) ((my - sh / 2.0) / (sh / 2.0));

        float mouseDX = normX - prevMouseX;
        float mouseDY = normY - prevMouseY;
        prevMouseX = normX;
        prevMouseY = normY;

        if (Math.abs(mouseDX) > 0.002f || Math.abs(mouseDY) > 0.002f) {
            mouseStillSince = lifeSeconds;
            mouseMoving = true;
            lastActivity = lifeSeconds;
        } else if (mouseMoving && lifeSeconds - mouseStillSince > 2.0) {
            mouseMoving = false;
        }

        boolean left = mc.mouseHandler.isLeftPressed();
        boolean right = mc.mouseHandler.isRightPressed();
        if (left && !prevLeftDown) {
            recordClick(lifeSeconds);
            lastActivity = lifeSeconds;
        }
        if (right && !prevRightDown) {
            recordClick(lifeSeconds);
            lastActivity = lifeSeconds;
        }
        prevLeftDown = left;
        prevRightDown = right;

        setParamBoth(pMouseX, normX * sens);
        setParamBoth(pMouseY, normY * sens);
        setParamBoth(pLeftDown, left ? 1f : 0f);
        setParamBoth(pRightDown, right ? 1f : 0f);
        setParamBoth(pKeyDown, anyKeyDown(mc) ? 1f : 0f);
        updateKeyboard(mc, dt);

        expression.update(dt, normX, normY, mouseMoving, lifeSeconds - mouseStillSince,
                recentPresses(), recentClicks(), Math.abs(mouseDX) > 0.12f || Math.abs(mouseDY) > 0.12f,
                lifeSeconds - lastActivity, lifeSeconds);

        float eyeX = normX * sens;
        float eyeY = normY * sens;
        if (!mouseMoving && lifeSeconds - mouseStillSince > 3.0) {
            float idleTime = (float) (lifeSeconds - mouseStillSince - 3.0);
            float blend = Math.min(1f, idleTime / 2.0f) * 0.65f;
            wanderX = (float) (Math.sin(lifeSeconds * 0.4) * 0.45 + Math.sin(lifeSeconds * 0.17) * 0.2);
            wanderY = (float) (Math.sin(lifeSeconds * 0.31 + 1.2) * 0.3 + Math.cos(lifeSeconds * 0.23) * 0.15);
            eyeX = eyeX * (1f - blend) + wanderX * blend;
            eyeY = eyeY * (1f - blend) + wanderY * blend;
        }

        setParamBoth(pEyeBallX, clamp(eyeX, -1f, 1f));
        setParamBoth(pEyeBallY, clamp(eyeY, -1f, 1f));

        float mouth = mouthOpen;
        if (expression.mouthOpenOverride >= 0f) {
            mouth = expression.mouthOpenOverride;
        }
        setParamBoth(pMouthOpen, mouth);

        float eyelid = blink.tick(dt, expression.blinkMul);
        float open = clamp(eyelid * expression.eyeOpenMod, 0f, 1f);
        setParamBoth(pEyeL, open);
        setParamBoth(pEyeR, open);
        setParamBoth(pBreath, 0.5f + 0.5f * (float) Math.sin(lifeSeconds * Math.PI / 2.2));

        setParamBoth(pAngleX, expression.angleX);
        setParamBoth(pAngleY, expression.angleY);
        setParamBoth(pAngleZ, expression.angleZ);
        setParamBoth(pBodyAngleX, expression.bodyAngleX);
        setParamBoth(pBodyAngleY, expression.bodyAngleY);
        setParamBoth(pBodyAngleZ, expression.bodyAngleZ);
        setParamBoth(pHairFront, expression.hairFront);
        setParamBoth(pHairSide, expression.hairSide);
        setParamBoth(pHairBack, expression.hairBack);
        setParamBoth(pBrowLY, expression.browLY);
        setParamBoth(pBrowRY, expression.browRY);
        setParamBoth(pBrowLAngle, expression.browLAngle);
        setParamBoth(pBrowRAngle, expression.browRAngle);
        setParamBoth(pBrowLForm, expression.browLForm);
        setParamBoth(pBrowRForm, expression.browRForm);
        setParamBoth(pMouthForm, expression.mouthForm);
        setParamBoth(pCheek, expression.cheek);
        setParamBoth(pEyeLSmile, expression.eyeSmile);
        setParamBoth(pEyeRSmile, expression.eyeSmile);
    }

    private float recentPresses() {
        int n = 0;
        double cut = lifeSeconds - 1.0;
        for (double t : keyLast) {
            if (t >= cut) {
                n++;
            }
        }
        return n;
    }

    private void recordClick(double t) {
        clickEvents[clickPos] = t;
        clickPos = (clickPos + 1) % clickEvents.length;
    }

    private float recentClicks() {
        int n = 0;
        double cut = lifeSeconds - 1.0;
        for (double c : clickEvents) {
            if (c >= cut) {
                n++;
            }
        }
        return n;
    }

    private void updateKeyboard(Minecraft mc, float dt) {
        int best = -1;
        double bestT = -1;
        for (int i = 0; i < MAPPED_KEYS.length; i++) {
            boolean down = InputConstants.isKeyDown(mc.getWindow(), MAPPED_KEYS[i]);
            if (down && !keyPrev[i]) {
                keyLast[i] = lifeSeconds;
                lastActivity = lifeSeconds;
            }
            keyPrev[i] = down;
            if (down && keyLast[i] > bestT) {
                bestT = keyLast[i];
                best = i;
            }
        }
        activeKey = best;
        mouthOpen = best >= 0 ? 1f : Math.max(0f, mouthOpen - dt * 6f);
    }

    /**
     * Writes param value into the snapshot array. Clamped to the param's
     * min/max from the model metadata. The compositor reads these values
     * via {@link CatGpuCompositor#updateSnapshot}.
     */
    private void setParamBoth(int index, float value) {
        if (index < 0 || index >= paramValues.length) return;
        float min = mmd[index * 3];
        float max = mmd[index * 3 + 1];
        paramValues[index] = clamp(value, min, max);
    }

    private float getParam(int index) {
        if (index < 0 || index >= paramValues.length) return -1f;
        return paramValues[index];
    }

    private int sampleChecksum() {
        if (catFrame == null) return 0;
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            int px = Cell(i * 87) % CatRenderer.W;
            int py = (i * 53) % CatRenderer.H;
            sum = sum * 31 + catFrame.getPixel(px, py);
        }
        return sum;
    }

    private int Cell(int v) {
        return v < 0 ? -v : v;
    }

    private int findParam(String id) {
        for (int i = 0; i < paramIds.length; i++) {
            if (paramIds[i].equals(id)) {
                return i;
            }
        }
        LOGGER.warn("[BongoCat] param not found: {}", id);
        return -1;
    }

    private HudProfile.ModuleSettings catSettings() {
        try {
            HudProfileManager mgr = HudProfileManager.getInstance();
            mgr.ensureLoaded();
            HudProfile profile = mgr.getCurrentProfile();
            if (profile != null) {
                return profile.getModuleSettings("bango_cat");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void fillFrame(ByteBuffer rows) {
        rows.position(0);
        if (opacityMul >= 0.999f) {
            for (int y = 0; y < CatRenderer.H; y++) {
                for (int x = 0; x < CatRenderer.W; x++) {
                    int r = rows.get() & 0xFF;
                    int g = rows.get() & 0xFF;
                    int b = rows.get() & 0xFF;
                    int a = rows.get() & 0xFF;
                    catFrame.setPixel(x, y, ARGB.color(a, r, g, b));
                }
            }
            return;
        }
        for (int y = 0; y < CatRenderer.H; y++) {
            for (int x = 0; x < CatRenderer.W; x++) {
                int r = rows.get() & 0xFF;
                int g = rows.get() & 0xFF;
                int b = rows.get() & 0xFF;
                int a = Math.round((rows.get() & 0xFF) * opacityMul);
                catFrame.setPixel(x, y, ARGB.color(a, r, g, b));
            }
        }
    }

    private static boolean anyKeyDown(Minecraft mc) {
        for (int key : KEYS) {
            if (InputConstants.isKeyDown(mc.getWindow(), key)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = CatHud.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("resource not found: " + path);
            }
            return in.readAllBytes();
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final int[] KEYS;

    private static final int[] MAPPED_KEYS = {
        InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
        InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6,
        InputConstants.KEY_7, InputConstants.KEY_Q, InputConstants.KEY_E,
        InputConstants.KEY_R, InputConstants.KEY_SPACE, InputConstants.KEY_A,
        InputConstants.KEY_D, InputConstants.KEY_S, InputConstants.KEY_W
    };

    static {
        int[] specials = {
            32, 257, 258, 259, 261,
            280, 268, 269, 263, 262, 265, 264,
            340, 341, 342, 344, 345, 346
        };
        int[] extra = {
            InputConstants.KEY_SEMICOLON, InputConstants.KEY_APOSTROPHE,
            InputConstants.KEY_COMMA, InputConstants.KEY_MINUS,
            InputConstants.KEY_PERIOD, InputConstants.KEY_SLASH,
            InputConstants.KEY_EQUALS, 91, 92, 93, 96
        };
        int count = 10 + 26 + specials.length + extra.length;
        KEYS = new int[count];
        int i = 0;
        for (int k = '0'; k <= '9'; k++) {
            KEYS[i++] = k;
        }
        for (int k = 'A'; k <= 'Z'; k++) {
            KEYS[i++] = k;
        }
        System.arraycopy(specials, 0, KEYS, i, specials.length);
        i += specials.length;
        System.arraycopy(extra, 0, KEYS, i, extra.length);
    }

    private static final class BlinkState {
        private static final double BLINK_DURATION = 0.14;
        private double untilNextBlink;
        private double blinkProgress = -1;

        BlinkState() {
            untilNextBlink = 2.0 + Math.random() * 4.0;
        }

        float tick(float dt, float mul) {
            if (blinkProgress >= 0) {
                blinkProgress += dt;
                if (blinkProgress >= BLINK_DURATION) {
                    blinkProgress = -1;
                    untilNextBlink = (2.0 + Math.random() * 4.0) * mul;
                    return 1f;
                }
                float t = (float) (blinkProgress / BLINK_DURATION);
                double s = Math.sin(t * Math.PI);
                return 1f - (float) (s * s) * 0.99f;
            }
            untilNextBlink -= dt;
            if (untilNextBlink <= 0) {
                blinkProgress = 0;
            }
            return 1f;
        }
    }
}
