package com.crest.client.core;

import java.util.Locale;

public class Easing {
    public enum Mode { IN, OUT, IN_OUT }

    public static float apply(String style, float t) {
        return apply(style, Mode.IN, t);
    }

    public static float apply(String style, Mode mode, float t) {
        if (t < 0) return 0;
        if (t > 1) return 1;
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "sine" -> switch (mode) {
                case IN -> sineIn(t);
                case OUT -> sineOut(t);
                case IN_OUT -> sineInOut(t);
            };
            case "quad" -> switch (mode) {
                case IN -> quadIn(t);
                case OUT -> quadOut(t);
                case IN_OUT -> quadInOut(t);
            };
            case "cubic" -> switch (mode) {
                case IN -> cubicIn(t);
                case OUT -> cubicOut(t);
                case IN_OUT -> cubicInOut(t);
            };
            case "quart" -> switch (mode) {
                case IN -> quartIn(t);
                case OUT -> quartOut(t);
                case IN_OUT -> quartInOut(t);
            };
            case "expo" -> switch (mode) {
                case IN -> expoIn(t);
                case OUT -> expoOut(t);
                case IN_OUT -> expoInOut(t);
            };
            case "back" -> switch (mode) {
                case IN -> backIn(t);
                case OUT -> backOut(t);
                case IN_OUT -> backInOut(t);
            };
            default -> t;
        };
    }

    /** OUT-mode convenience: {@code apply(style, Mode.OUT, t)}. */
    public static float out(String style, float t) {
        return apply(style, Mode.OUT, t);
    }

    /** IN-mode convenience: {@code apply(style, Mode.IN, t)}. */
    public static float in(String style, float t) {
        return apply(style, Mode.IN, t);
    }

    // Sine
    public static float sineIn(float t) { return 1 - (float) Math.cos(t * Math.PI / 2); }
    public static float sineOut(float t) { return (float) Math.sin(t * Math.PI / 2); }
    public static float sineInOut(float t) { return -(float) (Math.cos(Math.PI * t) - 1) / 2; }

    // Quad
    public static float quadIn(float t) { return t * t; }
    public static float quadOut(float t) { return t * (2 - t); }
    public static float quadInOut(float t) { return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t; }

    // Cubic
    public static float cubicIn(float t) { return t * t * t; }
    public static float cubicOut(float t) { return (float) (1 - Math.pow(1 - t, 3)); }
    public static float cubicInOut(float t) { return t < 0.5f ? 4 * t * t * t : (float) (1 - Math.pow(-2 * t + 2, 3) / 2); }

    // Quart
    public static float quartIn(float t) { return t * t * t * t; }
    public static float quartOut(float t) { return (float) (1 - Math.pow(1 - t, 4)); }
    public static float quartInOut(float t) { return t < 0.5f ? 8 * t * t * t * t : (float) (1 - Math.pow(-2 * t + 2, 4) / 2); }

    // Expo
    public static float expoIn(float t) { return t == 0 ? 0 : (float) Math.pow(2, 10 * (t - 1)); }
    public static float expoOut(float t) { return t == 1 ? 1 : (float) (1 - Math.pow(2, -10 * t)); }
    public static float expoInOut(float t) {
        if (t == 0 || t == 1) return t;
        return t < 0.5f ? (float) Math.pow(2, 20 * t - 10) / 2 : (float) (2 - Math.pow(2, -20 * t + 10)) / 2;
    }

    // Back (overshoot)
    public static float backIn(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (c3 * t - c1) * t * t;
    }

    public static float backOut(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float backInOut(float t) {
        float c2 = 2.5949095f;
        return t < 0.5f
            ? ((t * 2) * (t * 2) * ((c2 + 1) * t * 2 - c2)) / 2
            : (((t * 2) - 2) * ((t * 2) - 2) * ((c2 + 1) * ((t * 2) - 2) + c2) + 2) / 2;
    }

    // Lerp helper
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
