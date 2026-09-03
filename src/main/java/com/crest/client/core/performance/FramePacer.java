package com.crest.client.core.performance;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

/**
 * Frame pacing system to reduce micro-stutter by smoothing frame delivery.
 * Helps maintain consistent frame times even at high FPS (200+).
 */
public class FramePacer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("FramePacer");
    
    private static long lastFrameNano = 0;
    private static long targetFrameTimeNano = 0;
    private static boolean enabled = true;
    
    // Running average of frame times for adaptive pacing
    private static final int HISTORY_SIZE = 60;
    private static final long[] frameTimeHistory = new long[HISTORY_SIZE];
    private static int historyIndex = 0;
    private static long averageFrameTime = 0;
    
    public static void setEnabled(boolean enable) {
        enabled = enable;
        if (!enable) {
            lastFrameNano = 0;
            targetFrameTimeNano = 0;
        }
    }
    
    public static void setTargetFPS(int fps) {
        if (fps <= 0) {
            targetFrameTimeNano = 0;
        } else {
            targetFrameTimeNano = 1_000_000_000L / fps;
        }
    }
    
    /**
     * Call at the end of each frame to pace frame delivery.
     * Reduces micro-stutter by maintaining consistent frame times.
     */
    public static void endFrame() {
        if (!enabled) return;
        
        long now = System.nanoTime();
        
        if (lastFrameNano > 0) {
            long actualFrameTime = now - lastFrameNano;
            
            // Update frame time history
            frameTimeHistory[historyIndex] = actualFrameTime;
            historyIndex = (historyIndex + 1) % HISTORY_SIZE;
            
            // Calculate average (every 60 frames)
            if (historyIndex == 0) {
                long sum = 0;
                for (long time : frameTimeHistory) {
                    sum += time;
                }
                averageFrameTime = sum / HISTORY_SIZE;
            }
            
            // If we have a target FPS and rendered too fast, pace the frame
            if (targetFrameTimeNano > 0 && actualFrameTime < targetFrameTimeNano) {
                long sleepNano = targetFrameTimeNano - actualFrameTime;
                
                // Only sleep if difference is significant (> 0.5ms)
                if (sleepNano > 500_000) {
                    try {
                        // Use nano sleep for more precise timing
                        long sleepMs = sleepNano / 1_000_000;
                        int sleepNs = (int) (sleepNano % 1_000_000);
                        
                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs, sleepNs);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        lastFrameNano = System.nanoTime();
    }
    
    public static long getAverageFrameTimeNano() {
        return averageFrameTime;
    }
    
    public static double getAverageFPS() {
        if (averageFrameTime == 0) return 0;
        return 1_000_000_000.0 / averageFrameTime;
    }
    
    /**
     * Detects if frame times are inconsistent (micro-stutter).
     * Returns variance in frame times as a percentage.
     */
    public static double getFrameTimeVariance() {
        if (averageFrameTime == 0) return 0;
        
        long sumSquaredDiff = 0;
        for (long time : frameTimeHistory) {
            if (time > 0) {
                long diff = time - averageFrameTime;
                sumSquaredDiff += diff * diff;
            }
        }
        
        double variance = Math.sqrt((double) sumSquaredDiff / HISTORY_SIZE);
        return (variance / averageFrameTime) * 100.0; // as percentage
    }
}
