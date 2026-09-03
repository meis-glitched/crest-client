package com.crest.client.ui.animation;

import com.crest.client.core.Easing;
import com.crest.client.ui.Anim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Advanced animation engine supporting multiple concurrent animations with
 * callbacks, delays, and sequencing.
 */
public class AnimationEngine {
    private static final AnimationEngine INSTANCE = new AnimationEngine();
    
    private final List<Animation> animations = new ArrayList<>();
    private final List<Animation> toAdd = new ArrayList<>();
    private final List<Animation> toRemove = new ArrayList<>();
    
    public static AnimationEngine getInstance() {
        return INSTANCE;
    }
    
    public void tick(float delta) {
        // Add pending animations
        if (!toAdd.isEmpty()) {
            animations.addAll(toAdd);
            toAdd.clear();
        }
        
        // Update all animations
        for (Animation anim : animations) {
            anim.tick(delta);
            if (anim.isFinished()) {
                anim.onComplete();
                toRemove.add(anim);
            }
        }
        
        // Remove finished animations
        if (!toRemove.isEmpty()) {
            animations.removeAll(toRemove);
            toRemove.clear();
        }
    }
    
    public AnimationBuilder animate(float from, float to) {
        return new AnimationBuilder(from, to);
    }
    
    public void clear() {
        animations.clear();
        toAdd.clear();
        toRemove.clear();
    }
    
    public int getActiveCount() {
        return animations.size();
    }
    
    public class AnimationBuilder {
        private float from;
        private float to;
        private float duration = 0.3f;
        private float delay = 0f;
        private String easing = "cubic";
        private Consumer<Float> onUpdate;
        private Runnable onComplete;
        
        AnimationBuilder(float from, float to) {
            this.from = from;
            this.to = to;
        }
        
        public AnimationBuilder duration(float seconds) {
            this.duration = seconds;
            return this;
        }
        
        public AnimationBuilder delay(float seconds) {
            this.delay = seconds;
            return this;
        }
        
        public AnimationBuilder easing(String easing) {
            this.easing = easing;
            return this;
        }
        
        public AnimationBuilder onUpdate(Consumer<Float> callback) {
            this.onUpdate = callback;
            return this;
        }
        
        public AnimationBuilder onComplete(Runnable callback) {
            this.onComplete = callback;
            return this;
        }
        
        public void start() {
            Animation anim = new Animation(from, to, duration, delay, easing, onUpdate, onComplete);
            toAdd.add(anim);
        }
    }
    
    private static class Animation {
        private final float from;
        private final float to;
        private final float duration;
        private final float delay;
        private final String easing;
        private final Consumer<Float> onUpdate;
        private final Runnable onCompleteCallback;
        
        private float elapsed = 0f;
        private boolean started = false;
        
        Animation(float from, float to, float duration, float delay, String easing,
                  Consumer<Float> onUpdate, Runnable onComplete) {
            this.from = from;
            this.to = to;
            this.duration = duration;
            this.delay = delay;
            this.easing = easing;
            this.onUpdate = onUpdate;
            this.onCompleteCallback = onComplete;
        }
        
        void tick(float delta) {
            elapsed += delta;
            
            if (elapsed < delay) {
                return;
            }
            
            if (!started) {
                started = true;
                if (onUpdate != null) {
                    onUpdate.accept(from);
                }
            }
            
            float animTime = elapsed - delay;
            float t = Anim.clamp(animTime / duration, 0f, 1f);
            float eased = Easing.out(easing, t);
            float value = Anim.lerp(from, to, eased);
            
            if (onUpdate != null) {
                onUpdate.accept(value);
            }
        }
        
        boolean isFinished() {
            return elapsed >= delay + duration;
        }
        
        void onComplete() {
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        }
    }
}
