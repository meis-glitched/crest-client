package com.crest.client.ui;

import com.crest.client.core.Easing;

/**
 * Duration-driven eased progress (one-shot, forward or reverse) with an optional
 * leading delay. Unlike {@link Animated}, motion spans a fixed wall-clock time so
 * it reads consistently regardless of frame rate, and the eased output may
 * overshoot [0,1] for springy (Back) styles.
 */
public final class Tween {
    private float duration;
    private float delay;
    private String easing = "cubic";
    private boolean forward = true;
    private boolean started;
    private boolean running;
    private float elapsed;

    public Tween(float duration) {
        this.duration = Math.max(0.001f, duration);
    }

    public Tween(float duration, String easing) {
        this(duration);
        this.easing = easing == null ? "cubic" : easing;
    }

    public Tween setDuration(float seconds) {
        this.duration = Math.max(0.001f, seconds);
        return this;
    }

    public Tween setDelay(float seconds) {
        this.delay = Math.max(0, seconds);
        return this;
    }

    public Tween setEasing(String style) {
        this.easing = style == null ? "cubic" : style;
        return this;
    }

    public void startForward() {
        startRun(true);
    }

    public void startReverse() {
        startRun(false);
    }

    /** Snap the animation to its finished end state (used when animation is disabled). */
    public void startFinished() {
        forward = true;
        started = true;
        running = false;
        elapsed = delay + duration;
    }

    public void reset() {
        started = false;
        running = false;
        elapsed = 0;
    }

    private void startRun(boolean fwd) {
        forward = fwd;
        started = true;
        running = true;
        elapsed = 0;
    }

    public void tick(float dt) {
        if (!running) return;
        elapsed += dt;
        if (elapsed >= delay + duration) {
            elapsed = delay + duration;
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean finishedForward() {
        return started && !running && forward;
    }

    public boolean finishedReverse() {
        return started && !running && !forward;
    }

    /** 0..1 clock position within the eased segment (ignores the delay). */
    public float raw() {
        float animTime = elapsed - delay;
        return Anim.clamp(animTime / duration, 0f, 1f);
    }

    /**
     * Eased output: forward runs 0-&gt;1, reverse runs 1-&gt;0. May overshoot [0,1]
     * for back-style curves; the reverse mirrors the forward curve in time.
     */
    public float value() {
        if (!started) return forward ? 0f : 1f;
        if (finishedForward()) return 1f;
        if (finishedReverse()) return 0f;
        float p = raw();
        return forward ? Easing.out(easing, p) : Easing.out(easing, 1f - p);
    }
}