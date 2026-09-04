package com.crest.client.ui;

/** ARGB color manipulation helpers. Colors are packed 0xAARRGGBB. */
public final class ColorUtil {
    private ColorUtil() {}

    public static int rgba(int r, int g, int b, int a) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int argb(int a, int r, int g, int b) {
        return rgba(r, g, b, a);
    }

    public static int getR(int c) { return (c >> 16) & 0xFF; }
    public static int getG(int c) { return (c >> 8) & 0xFF; }
    public static int getB(int c) { return c & 0xFF; }
    public static int getA(int c) { return (c >> 24) & 0xFF; }

    public static int withAlpha(int c, int a) {
        return (c & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int setAlpha(int c, int a) {
        return (c & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    /** Mix two colors with a blend factor (0.0 = colorA, 1.0 = colorB) */
    public static int mix(int colorA, int colorB, float blend) {
        return lerpARGB(colorA, colorB, blend);
    }
    
    /** Linearly interpolate between two packed ARGB colors in premultiplied alpha space for correct blending. */
    public static int lerpARGB(int a, int b, float t) {
        int aA = getA(a), aR = getR(a), aG = getG(a), aB = getB(a);
        int bA = getA(b), bR = getR(b), bG = getG(b), bB = getB(b);
        float aA_n = aA / 255f;
        float bA_n = bA / 255f;
        float paR = aA_n * aR, paG = aA_n * aG, paB = aA_n * aB;
        float pbR = bA_n * bR, pbG = bA_n * bG, pbB = bA_n * bB;
        float lerpR = paR + (pbR - paR) * t;
        float lerpG = paG + (pbG - paG) * t;
        float lerpB = paB + (pbB - paB) * t;
        float lerpA = aA_n + (bA_n - aA_n) * t;
        int outA = Math.round(lerpA * 255);
        if (outA <= 0) return 0;
        int outR = Math.round(lerpR / lerpA);
        int outG = Math.round(lerpG / lerpA);
        int outB = Math.round(lerpB / lerpA);
        return rgba(Math.min(255, outR), Math.min(255, outG), Math.min(255, outB), Math.min(255, outA));
    }

    /** Subtle hue rotation for animated accents. t in [0,1) loops. */
    public static int hueShift(int base, float t) {
        float h = (float) (t % 1.0f);
        if (h < 0) h += 1f;
        float[] hsv = toHSV(base);
        return hsvToInt(h, hsv[1], hsv[2], getA(base) / 255f);
    }

    public static int hsvToInt(float h, float s, float v, float a) {
        int hi = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        int p = Math.round(255 * v * (1 - s));
        int q = Math.round(255 * v * (1 - f * s));
        int t2 = Math.round(255 * v * (1 - (1 - f) * s));
        int w = Math.round(255 * v);
        int r, g, b;
        switch (hi) {
            case 0: r = w; g = t2; b = p; break;
            case 1: r = q; g = w; b = p; break;
            case 2: r = p; g = w; b = t2; break;
            case 3: r = p; g = q; b = w; break;
            case 4: r = t2; g = p; b = w; break;
            default: r = w; g = p; b = q; break;
        }
        return rgba(r, g, b, Math.round(a * 255));
    }

    /** Convert HSV (h,s,v in 0..1) to packed ARGB with full alpha. */
    public static int hsvToArgb(float h, float s, float v) {
        return hsvToInt(h, s, v, 1f);
    }

    /** Shorthand for hsvToArgb. */
    public static int hsv(float h, float s, float v) {
        return hsvToInt(h, s, v, 1f);
    }

    /** Convert packed ARGB to HSV (h,s,v in 0..1). Alpha ignored. */
    public static float[] toHSV(int c) {
        int r = getR(c), g = getG(c), b = getB(c);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float v = max / 255f;
        float d = (max - min) / 255f;
        float s = max == 0 ? 0f : d / v;
        float h;
        if (d == 0) h = 0f;
        else if (max == r) h = ((g - b) / 255f) / d;
        else if (max == g) h = 2f + ((b - r) / 255f) / d;
        else h = 4f + ((r - g) / 255f) / d;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, s, v};
    }
}
