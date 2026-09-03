package com.crest.client.waypoints.io;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.crest.client.waypoints.Waypoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WaypointExporter {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("WaypointExporter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public enum Format {
        JSON,
        CSV,
        XAEROS
    }
    
    public static void exportToFile(List<Waypoint> waypoints, Path outputFile, Format format) throws IOException {
        LOGGER.info("Exporting {} waypoints to {} (format: {})", waypoints.size(), outputFile, format);
        
        switch (format) {
            case JSON -> exportJson(waypoints, outputFile);
            case CSV -> exportCsv(waypoints, outputFile);
            case XAEROS -> exportXaeros(waypoints, outputFile);
        }
        
        LOGGER.info("Export complete");
    }
    
    private static void exportJson(List<Waypoint> waypoints, Path outputFile) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 11);
        root.addProperty("format", "crest-waypoints");
        root.addProperty("exported_at", System.currentTimeMillis());
        
        JsonArray waypointsArray = new JsonArray();
        for (Waypoint wp : waypoints) {
            JsonObject wpObj = new JsonObject();
            wpObj.addProperty("id", wp.uuid);
            wpObj.addProperty("name", wp.name);
            wpObj.addProperty("x", wp.pos.getX());
            wpObj.addProperty("y", wp.pos.getY());
            wpObj.addProperty("z", wp.pos.getZ());
            wpObj.addProperty("dimension", wp.dimension.location().toString());
            wpObj.addProperty("color", String.format("#%06X", wp.color & 0xFFFFFF));
            if (wp.iconId != null) {
                wpObj.addProperty("icon", wp.iconId);
            }
            waypointsArray.add(wpObj);
        }
        root.add("waypoints", waypointsArray);
        
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            GSON.toJson(root, writer);
        }
    }
    
    private static void exportCsv(List<Waypoint> waypoints, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write("name,x,y,z,dimension,color,icon,description\n");
            
            for (Waypoint wp : waypoints) {
                BlockPos pos = wp.pos;
                String color = String.format("#%06X", wp.color & 0xFFFFFF);
                String icon = wp.iconId != null ? wp.iconId : "";
                String description = ""; // Add description field if available
                
                writer.write(String.format("\"%s\",%d,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    escapeCsv(wp.name),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    wp.dimension.location().toString(),
                    color,
                    icon,
                    description));
            }
        }
    }
    
    private static void exportXaeros(List<Waypoint> waypoints, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (Waypoint wp : waypoints) {
                BlockPos pos = wp.pos;
                
                // Xaero's format: waypoint:Name:Symbol:X:Y:Z:Color:disabled:type:set:dimension
                String dimension = wp.dimension.location().toString();
                String xaerosDim = convertToXaerosDimension(dimension);
                
                int color = convertColorToXaeros(wp.color);
                String symbol = wp.iconId != null ? wp.iconId.substring(0, 1).toUpperCase() : "W";
                
                writer.write(String.format("waypoint:%s:%s:%d:%d:%d:%d:false:0:Internal-%s\n",
                    escapeName(wp.name),
                    symbol,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    color,
                    xaerosDim));
            }
        }
    }
    
    private static String convertToXaerosDimension(String minecraftDim) {
        if (minecraftDim.contains("overworld")) return "overworld-waypoints";
        if (minecraftDim.contains("nether")) return "the_nether-waypoints";
        if (minecraftDim.contains("end")) return "the_end-waypoints";
        return "overworld-waypoints";
    }
    
    private static int convertColorToXaeros(int crestColor) {
        // Convert RGB to Xaero's color index (0-15)
        int r = (crestColor >> 16) & 0xFF;
        int g = (crestColor >> 8) & 0xFF;
        int b = crestColor & 0xFF;
        
        // Simple mapping to 16 colors
        if (r > 200 && g < 100 && b < 100) return 0; // Red
        if (r < 100 && g > 200 && b < 100) return 2; // Green
        if (r < 100 && g < 100 && b > 200) return 3; // Blue
        if (r > 200 && g > 200 && b < 100) return 4; // Yellow
        if (r > 200 && g < 100 && b > 200) return 5; // Magenta
        if (r < 100 && g > 200 && b > 200) return 6; // Cyan
        if (r > 200 && g > 200 && b > 200) return 7; // White
        return 0; // Default to red
    }
    
    private static String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
    
    private static String escapeName(String name) {
        if (name == null) return "Waypoint";
        return name.replace(":", "-");
    }
}
