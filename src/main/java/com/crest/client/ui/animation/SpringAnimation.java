package com.crest.client.ui.animation;

import com.crest.client.core.Easing;
import com.crest.client.ui.Anim;

/**
 * Spring-based physics animation for natural-feeling motion.
 */
public class SpringAnimation {
    private float position;
    private float velocity;
    private float target;
    
    private float stiffness;  // How quickly it returns to target (higher = faster)
    private float damping;    // How much it resists motion (higher = less bouncy)
    private float mass;       // Affects inertia (higher = slower to respond)
    
    public SpringAnimation(float initial) {
        this(initial, 300f, 30f, 1f);
    }
    
    public SpringAnimation(float initial, float stiffness, float damping, float mass) {
        this.position = initial;
        this.target = initial;
        this.velocity = 0f;
        this.stiffness = stiffness;
        this.damping = damping;
        this.mass = mass;
    }
    
    public void setTarget(float target) {
        this.target = target;
    }
    
    public void setImmediate(float value) {
        this.position = value;
        this.target = value;
        this.velocity = 0f;
    }
    
    public void tick(float dt) {
        // Spring physics: F = -k*x - c*v
        float displacement = position - target;
        float springForce = -stiffness * displacement;
        float dampingForce = -damping * velocity;
        float acceleration = (springForce + dampingForce) / mass;
        
        velocity += acceleration * dt;
        position += velocity * dt;
        
        // Stop if very close to target and nearly still
        if (Math.abs(displacement) < 0.001f && Math.abs(velocity) < 0.001f) {
            position = target;
            velocity = 0f;
        }
    }
    
    public float get() {
        return position;
    }
    
    public float getTarget() {
        return target;
    }
    
    public float getVelocity() {
        return velocity;
    }
    
    public boolean isSettled() {
        return Math.abs(position - target) < 0.001f && Math.abs(velocity) < 0.001f;
    }
    
    // Preset configurations
    public static SpringAnimation gentle(float initial) {
        return new SpringAnimation(initial, 150f, 25f, 1f);
    }
    
    public static SpringAnimation bouncy(float initial) {
        return new SpringAnimation(initial, 400f, 20f, 1f);
    }
    
    public static SpringAnimation stiff(float initial) {
        return new SpringAnimation(initial, 500f, 40f, 1f);
    }
}
