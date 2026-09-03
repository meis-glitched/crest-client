package com.crest.client.waypoints.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;

public class PathResult {
    private final boolean success;
    private final List<BlockPos> path;
    private final String errorMessage;
    private final long computeTimeMs;
    
    private PathResult(boolean success, List<BlockPos> path, String errorMessage, long computeTimeMs) {
        this.success = success;
        this.path = path;
        this.errorMessage = errorMessage;
        this.computeTimeMs = computeTimeMs;
    }
    
    public static PathResult success(List<BlockPos> path, long computeTimeMs) {
        return new PathResult(true, path, null, computeTimeMs);
    }
    
    public static PathResult failure(String errorMessage) {
        return new PathResult(false, Collections.emptyList(), errorMessage, 0);
    }
    
    public boolean isSuccess() { return success; }
    public List<BlockPos> getPath() { return path; }
    public String getErrorMessage() { return errorMessage; }
    public long getComputeTimeMs() { return computeTimeMs; }
    
    public double getTotalDistance() {
        if (!success || path.size() < 2) return 0;
        
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += Math.sqrt(path.get(i).distSqr(path.get(i + 1)));
        }
        return total;
    }
    
    public BlockPos getNextWaypoint() {
        if (!success || path.isEmpty()) return null;
        return path.get(0);
    }
}
