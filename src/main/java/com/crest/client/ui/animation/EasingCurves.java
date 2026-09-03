package com.crest.client.ui.animation;

/**
 * Collection of common easing curves for animations.
 * Extends the base Easing class with additional curves.
 */
public class EasingCurves {
    
    // Elastic easing - overshoots with oscillation
    public static float easeOutElastic(float t) {
        if (t == 0 || t == 1) return t;
        float p = 0.3f;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
    }
    
    // Bounce easing - bounces at the end
    public static float easeOutBounce(float t) {
        if (t < 1 / 2.75) {
            return 7.5625f * t * t;
        } else if (t < 2 / 2.75) {
            t -= 1.5f / 2.75f;
            return 7.5625f * t * t + 0.75f;
        } else if (t < 2.5 / 2.75) {
            t -= 2.25f / 2.75f;
            return 7.5625f * t * t + 0.9375f;
        } else {
            t -= 2.625f / 2.75f;
            return 7.5625f * t * t + 0.984375f;
        }
    }
    
    // Expo easing - exponential acceleration/deceleration
    public static float easeOutExpo(float t) {
        return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    }
    
    public static float easeInExpo(float t) {
        return t == 0 ? 0 : (float) Math.pow(2, 10 * (t - 1));
    }
    
    // Circ easing - circular arc
    public static float easeOutCirc(float t) {
        t--;
        return (float) Math.sqrt(1 - t * t);
    }
    
    public static float easeInCirc(float t) {
        return 1 - (float) Math.sqrt(1 - t * t);
    }
    
    // Sine easing - smooth sinusoidal
    public static float easeOutSine(float t) {
        return (float) Math.sin(t * Math.PI / 2);
    }
    
    public static float easeInSine(float t) {
        return 1 - (float) Math.cos(t * Math.PI / 2);
    }
    
    // Quad easing - quadratic
    public static float easeOutQuad(float t) {
        return t * (2 - t);
    }
    
    public static float easeInQuad(float t) {
        return t * t;
    }
    
    // Quart easing - quartic
    public static float easeOutQuart(float t) {
        t--;
        return 1 - t * t * t * t;
    }
    
    public static float easeInQuart(float t) {
        return t * t * t * t;
    }
    
    // Quint easing - quintic
    public static float easeOutQuint(float t) {
        t--;
        return 1 + t * t * t * t * t;
    }
    
    public static float easeInQuint(float t) {
        return t * t * t * t * t;
    }
}
