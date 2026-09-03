package com.crest.client.ui.animation;

import com.crest.client.ui.Anim;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages smooth transitions between UI states with support for multiple
 * simultaneous transitions and callbacks.
 */
public class TransitionManager {
    private final List<Transition> transitions = new ArrayList<>();
    
    public void tick(float delta) {
        transitions.removeIf(t -> {
            t.tick(delta);
            return t.isComplete();
        });
    }
    
    public Transition begin(float from, float to, float duration) {
        Transition t = new Transition(from, to, duration);
        transitions.add(t);
        return t;
    }
    
    public void clear() {
        transitions.clear();
    }
    
    public static class Transition {
        private final float from;
        private final float to;
        private final float duration;
        private float elapsed = 0f;
        private boolean complete = false;
        private Runnable onComplete;
        
        Transition(float from, float to, float duration) {
            this.from = from;
            this.to = to;
            this.duration = duration;
        }
        
        public Transition onComplete(Runnable callback) {
            this.onComplete = callback;
            return this;
        }
        
        void tick(float delta) {
            if (complete) return;
            
            elapsed += delta;
            if (elapsed >= duration) {
                elapsed = duration;
                complete = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }
        
        public float getValue() {
            float t = Anim.clamp(elapsed / duration, 0f, 1f);
            return Anim.lerp(from, to, t);
        }
        
        public float getEased(String easing) {
            float t = Anim.clamp(elapsed / duration, 0f, 1f);
            float eased = com.crest.client.core.Easing.out(easing, t);
            return Anim.lerp(from, to, eased);
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public float getProgress() {
            return Anim.clamp(elapsed / duration, 0f, 1f);
        }
    }
}
