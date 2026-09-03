package com.crest.client.waypoints.pathfinding;

public enum PathMode {
    DIRECT,      // Straight line (ignores terrain)
    MANHATTAN,   // Grid-based movement
    EUCLIDEAN    // Natural diagonal movement
}
