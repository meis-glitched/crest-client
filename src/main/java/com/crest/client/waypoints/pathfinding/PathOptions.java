package com.crest.client.waypoints.pathfinding;

public record PathOptions(
    PathMode mode,
    boolean allowDiagonal,
    boolean allowFlying,
    boolean avoidDanger,
    double verticalCostMultiplier
) {
    public static PathOptions defaultOptions() {
        return new PathOptions(PathMode.EUCLIDEAN, true, false, true, 2.0);
    }
    
    public static PathOptions direct() {
        return new PathOptions(PathMode.DIRECT, true, true, false, 1.0);
    }
    
    public static PathOptions ground() {
        return new PathOptions(PathMode.EUCLIDEAN, true, false, true, 2.0);
    }
    
    public static PathOptions safe() {
        return new PathOptions(PathMode.MANHATTAN, false, false, true, 3.0);
    }
}
