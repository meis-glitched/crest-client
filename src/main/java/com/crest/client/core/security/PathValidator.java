package com.crest.client.core.security;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidator {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("PathValidator");
    
    public static String sanitizeFilename(String filename) {
        if (filename == null) return "";
        
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        filename = filename.replaceAll("\\.\\.", "_");
        
        if (filename.length() > 255) {
            filename = filename.substring(0, 255);
        }
        
        return filename;
    }
    
    public static boolean isPathSafe(Path path, Path baseDir) {
        try {
            Path normalized = path.normalize().toAbsolutePath();
            Path normalizedBase = baseDir.normalize().toAbsolutePath();
            
            boolean safe = normalized.startsWith(normalizedBase);
            
            if (!safe) {
                LOGGER.security("Path traversal attempt blocked: {} (base: {})", 
                    normalized, normalizedBase);
            }
            
            return safe;
        } catch (Exception e) {
            LOGGER.error("Error validating path", e);
            return false;
        }
    }
    
    public static boolean isPathSafe(String path, String baseDir) {
        try {
            return isPathSafe(Paths.get(path), Paths.get(baseDir));
        } catch (Exception e) {
            LOGGER.error("Error validating path strings", e);
            return false;
        }
    }
}
