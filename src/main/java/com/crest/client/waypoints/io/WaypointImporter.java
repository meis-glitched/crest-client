package com.crest.client.waypoints.io;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.waypoints.Waypoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WaypointImporter {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("WaypointImporter");
    
    public enum Format {
        JSON,
        CSV,
        XAEROS
    }
    
    public static List<Waypoint> importFromFile(Path inputFile, Format format) throws IOException {
        LOGGER.info("Importing waypoints from {} (format: {})", inputFile, format);
        
        List<Waypoint> waypoints = switch (format) {
            case JSON -> importJson(inputFile);
            case CSV -> importCsv(inputFile);
            case XAEROS -> importXaeros(inputFile);
        };
        
        LOGGER.info("Imported {} waypoints", waypoints.size());
        return waypoints;
    }
    
    private static List<Waypoint> importJson(Path inputFile) throws IOException {
        List<Waypoint> waypoints = new ArrayList<>();
        
        try (FileReader reader = new FileReader(inputFile.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray waypointsArray = root.getAsJsonArray("waypoints");
            
            for (int i = 0; i < waypointsArray.size(); i++) {
                JsonObject wpObj = waypointsArray.get(i).getAsJsonObject();
                
                String name = wpObj.get("name").getAsString();
                int x = wpObj.get("x").getAsInt();
                int y = wpObj.get("y").getAsInt();
                int z = wpObj.get("z").getAsInt();
                String dimension = wpObj.get("dimension").getAsString();
                String colorStr = wpObj.get("color").getAsString();
                
                int color = parseColor(colorStr);
                BlockPos pos = new BlockPos(x, y, z);
                
                Waypoint wp = new Waypoint(
                    UUID.randomUUID().toString(),
                    pos,
                    name,
                    color,
                    wpObj.has("icon") ? wpObj.get("icon").getAsString() : null,
                    false,
                    Waypoint.Type.NORMAL
                );
                
                waypoints.add(wp);
            }
        }
        
        return waypoints;
    }
    
    private static List<Waypoint> importCsv(Path inputFile) throws IOException {
        List<Waypoint> waypoints = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            String header = reader.readLine(); // Skip header
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 5) continue;
                
                String name = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                String dimension = parts[4];
                
                int color = parts.length > 5 ? parseColor(parts[5]) : 0xFF0000;
                String icon = parts.length > 6 ? parts[6] : null;
                
                BlockPos pos = new BlockPos(x, y, z);
                
                Waypoint wp = new Waypoint(
                    UUID.randomUUID().toString(),
                    pos,
                    name,
                    color,
                    icon,
                    false,
                    Waypoint.Type.NORMAL
                );
                
                waypoints.add(wp);
            }
        }
        
        return waypoints;
    }
    
    private static List<Waypoint> importXaeros(Path inputFile) throws IOException {
        List<Waypoint> waypoints = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("waypoint:")) continue;
                
                String[] parts = line.substring(9).split(":");
                if (parts.length < 9) continue;
                
                String name = parts[0];
                String symbol = parts[1];
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);
                int xaerosColor = Integer.parseInt(parts[5]);
                String xaerosDim = parts.length > 9 ? parts[9] : "Internal-overworld-waypoints";
                
                int color = convertXaerosColor(xaerosColor);
                BlockPos pos = new BlockPos(x, y, z);
                
                Waypoint wp = new Waypoint(
                    UUID.randomUUID().toString(),
                    pos,
                    name,
                    color,
                    symbol.toLowerCase(),
                    false,
                    Waypoint.Type.NORMAL
                );
                
                waypoints.add(wp);
            }
        }
        
        return waypoints;
    }
    
    private static int parseColor(String colorStr) {
        if (colorStr.startsWith("#")) {
            return Integer.parseInt(colorStr.substring(1), 16);
        }
        return Integer.parseInt(colorStr, 16);
    }
    
    private static ResourceKey<Level> parseDimension(String dimension) {
        if (dimension.contains("nether")) {
            return Level.NETHER;
        } else if (dimension.contains("end")) {
            return Level.END;
        }
        return Level.OVERWORLD;
    }
    
    private static ResourceKey<Level> convertXaerosDimension(String xaerosDim) {
        if (xaerosDim.contains("nether")) {
            return Level.NETHER;
        } else if (xaerosDim.contains("end")) {
            return Level.END;
        }
        return Level.OVERWORLD;
    }
    
    private static int convertXaerosColor(int xaerosColor) {
        // Convert Xaero's color index (0-15) to RGB
        return switch (xaerosColor) {
            case 0 -> 0xFF0000; // Red
            case 1 -> 0xFF8000; // Orange
            case 2 -> 0x00FF00; // Green
            case 3 -> 0x0000FF; // Blue
            case 4 -> 0xFFFF00; // Yellow
            case 5 -> 0xFF00FF; // Magenta
            case 6 -> 0x00FFFF; // Cyan
            case 7 -> 0xFFFFFF; // White
            case 8 -> 0x800000; // Dark Red
            case 9 -> 0x008000; // Dark Green
            case 10 -> 0x000080; // Dark Blue
            case 11 -> 0x808000; // Olive
            case 12 -> 0x800080; // Purple
            case 13 -> 0x008080; // Teal
            case 14 -> 0x808080; // Gray
            case 15 -> 0x000000; // Black
            default -> 0xFF0000;
        };
    }
    
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }
}
