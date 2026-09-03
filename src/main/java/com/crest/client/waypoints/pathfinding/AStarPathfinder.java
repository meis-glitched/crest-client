package com.crest.client.waypoints.pathfinding;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class AStarPathfinder {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("Pathfinding");
    
    private static final int MAX_ITERATIONS = 10000;
    private static final int MAX_DISTANCE = 1000;
    
    public static PathResult findPath(Level level, BlockPos start, BlockPos end, PathOptions options) {
        long startTime = System.currentTimeMillis();
        
        LOGGER.info("Finding path from {} to {}", start, end);
        
        if (start.distSqr(end) > MAX_DISTANCE * MAX_DISTANCE) {
            LOGGER.warn("Target too far ({} blocks)", Math.sqrt(start.distSqr(end)));
            return PathResult.failure("Target too far");
        }
        
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        
        PathNode startNode = new PathNode(start, null, 0, heuristic(start, end, options));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int iterations = 0;
        
        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            
            PathNode current = openSet.poll();
            
            if (current.pos.equals(end) || current.pos.distSqr(end) <= 4) {
                List<BlockPos> path = reconstructPath(current);
                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("Path found: {} nodes, {} iterations, {}ms", path.size(), iterations, duration);
                return PathResult.success(path, duration);
            }
            
            closedSet.add(current.pos);
            
            for (BlockPos neighbor : getNeighbors(current.pos, options)) {
                if (closedSet.contains(neighbor)) continue;
                
                if (!isWalkable(level, neighbor, options)) continue;
                
                double tentativeG = current.gScore + distance(current.pos, neighbor, options);
                
                PathNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new PathNode(neighbor, current, tentativeG, heuristic(neighbor, end, options));
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeG < neighborNode.gScore) {
                    openSet.remove(neighborNode);
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeG;
                    neighborNode.fScore = tentativeG + heuristic(neighbor, end, options);
                    openSet.add(neighborNode);
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        LOGGER.warn("No path found after {} iterations ({}ms)", iterations, duration);
        return PathResult.failure("No path found");
    }
    
    private static List<BlockPos> getNeighbors(BlockPos pos, PathOptions options) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        // Cardinal directions
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        
        // Diagonal movement if enabled
        if (options.allowDiagonal()) {
            neighbors.add(pos.north().east());
            neighbors.add(pos.north().west());
            neighbors.add(pos.south().east());
            neighbors.add(pos.south().west());
        }
        
        // Vertical movement
        neighbors.add(pos.above());
        neighbors.add(pos.below());
        
        // Jump up
        neighbors.add(pos.above().north());
        neighbors.add(pos.above().south());
        neighbors.add(pos.above().east());
        neighbors.add(pos.above().west());
        
        return neighbors;
    }
    
    private static boolean isWalkable(Level level, BlockPos pos, PathOptions options) {
        if (level == null) return true; // Allow pathfinding without world access
        
        BlockState blockAt = level.getBlockState(pos);
        BlockState blockAbove = level.getBlockState(pos.above());
        BlockState blockBelow = level.getBlockState(pos.below());
        
        // Can't walk through solid blocks
        if (!blockAt.isAir() && blockAt.canOcclude()) return false;
        if (!blockAbove.isAir() && blockAbove.canOcclude()) return false;
        
        // Need solid ground below (unless flying)
        if (!options.allowFlying() && blockBelow.isAir()) return false;
        
        // Avoid dangerous blocks
        if (options.avoidDanger()) {
            String blockName = blockAt.getBlock().getName().getString().toLowerCase();
            if (blockName.contains("lava") || blockName.contains("fire") || blockName.contains("magma")) {
                return false;
            }
        }
        
        return true;
    }
    
    private static double distance(BlockPos a, BlockPos b, PathOptions options) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        
        // Penalize vertical movement
        double cost = Math.sqrt(dx * dx + dz * dz) + dy * options.verticalCostMultiplier();
        
        return cost;
    }
    
    private static double heuristic(BlockPos pos, BlockPos goal, PathOptions options) {
        if (options.mode() == PathMode.DIRECT) {
            return pos.distSqr(goal);
        } else if (options.mode() == PathMode.MANHATTAN) {
            int dx = Math.abs(pos.getX() - goal.getX());
            int dy = Math.abs(pos.getY() - goal.getY());
            int dz = Math.abs(pos.getZ() - goal.getZ());
            return dx + dy + dz;
        } else {
            // Euclidean
            return Math.sqrt(pos.distSqr(goal));
        }
    }
    
    private static List<BlockPos> reconstructPath(PathNode node) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = node;
        while (current != null) {
            path.add(0, current.pos);
            current = current.parent;
        }
        return path;
    }
    
    private static class PathNode {
        BlockPos pos;
        PathNode parent;
        double gScore;
        double fScore;
        
        PathNode(BlockPos pos, PathNode parent, double gScore, double hScore) {
            this.pos = pos;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = gScore + hScore;
        }
    }
}
