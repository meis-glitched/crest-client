package com.crest.client.core.performance;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

/**
 * Render thread optimizer to prevent blocking and reduce micro-stutter.
 */
public class RenderOptimizer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("RenderOptimizer");
    
    // Skip rendering when frame budget is exceeded
    private static final long MAX_FRAME_TIME_NANO = 16_666_666; // ~60 FPS baseline
    private static long lastFrameStartNano = 0;
    private static int skippedFrames = 0;
    
    // Adaptive quality reduction
    private static float qualityMultiplier = 1.0f;
    private static boolean adaptiveQualityEnabled = true;
    
    public static void setAdaptiveQualityEnabled(boolean enabled) {
        adaptiveQualityEnabled = enabled;
        if (!enabled) {
            qualityMultiplier = 1.0f;
        }
    }
    
    public static void startFrame() {
        lastFrameStartNano = System.nanoTime();
    }
    
    public static boolean shouldSkipExpensiveRender() {
        if (!adaptiveQualityEnabled) return false;
        
        long elapsed = System.nanoTime() - lastFrameStartNano;
        
        // If frame is taking too long, skip expensive renders
        if (elapsed > MAX_FRAME_TIME_NANO * 0.8) {
            return true;
        }
        
        return false;
    }
    
    public static float getQualityMultiplier() {
        return qualityMultiplier;
    }
    
    public static void adjustQuality(long frameTimeNano) {
        if (!adaptiveQualityEnabled) return;
        
        // Adjust quality based on frame time
        if (frameTimeNano > MAX_FRAME_TIME_NANO * 1.2) {
            // Too slow, reduce quality
            qualityMultiplier = Math.max(0.5f, qualityMultiplier - 0.05f);
        } else if (frameTimeNano < MAX_FRAME_TIME_NANO * 0.8) {
            // Fast enough, increase quality
            qualityMultiplier = Math.min(1.0f, qualityMultiplier + 0.02f);
        }
    }
    
    /**
     * Call this when a frame is skipped to track performance issues.
     */
    public static void onFrameSkipped() {
        skippedFrames++;
        if (skippedFrames % 100 == 0) {
            LOGGER.warn("Skipped {} frames due to performance", skippedFrames);
        }
    }
    
    public static int getSkippedFrames() {
        return skippedFrames;
    }
    
    public static void reset() {
        skippedFrames = 0;
        qualityMultiplier = 1.0f;
    }
}
