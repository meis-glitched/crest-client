package com.crest.client.ui.animation;

import com.crest.client.ui.Anim;

/**
 * Particle-based animation system for visual effects like sparkles, confetti, etc.
 */
public class ParticleAnimation {
    private final Particle[] particles;
    private boolean active = false;
    private float duration;
    private float elapsed;
    
    public ParticleAnimation(int count, float duration) {
        this.particles = new Particle[count];
        this.duration = duration;
        for (int i = 0; i < count; i++) {
            particles[i] = new Particle();
        }
    }
    
    public void start(float x, float y) {
        active = true;
        elapsed = 0;
        
        for (Particle p : particles) {
            p.reset(x, y);
        }
    }
    
    public void tick(float delta) {
        if (!active) return;
        
        elapsed += delta;
        if (elapsed >= duration) {
            active = false;
            return;
        }
        
        for (Particle p : particles) {
            p.tick(delta);
        }
    }
    
    public boolean isActive() {
        return active;
    }
    
    public Particle[] getParticles() {
        return particles;
    }
    
    public float getAlpha() {
        return 1f - (elapsed / duration);
    }
    
    public static class Particle {
        public float x, y;
        public float vx, vy;
        public float size;
        public float rotation;
        public float rotationSpeed;
        public int color;
        
        void reset(float startX, float startY) {
            this.x = startX;
            this.y = startY;
            
            // Random velocity
            float angle = (float) (Math.random() * Math.PI * 2);
            float speed = (float) (Math.random() * 100 + 50);
            this.vx = (float) Math.cos(angle) * speed;
            this.vy = (float) Math.sin(angle) * speed;
            
            this.size = (float) (Math.random() * 3 + 2);
            this.rotation = (float) (Math.random() * Math.PI * 2);
            this.rotationSpeed = (float) (Math.random() * 4 - 2);
            
            // Random pastel color
            int r = (int) (Math.random() * 128 + 127);
            int g = (int) (Math.random() * 128 + 127);
            int b = (int) (Math.random() * 128 + 127);
            this.color = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        
        void tick(float delta) {
            x += vx * delta;
            y += vy * delta;
            
            // Gravity
            vy += 200 * delta;
            
            // Rotation
            rotation += rotationSpeed * delta;
            
            // Air resistance
            vx *= 0.98f;
        }
    }
}
