package com.crest.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the CrestRender native library (.so/.dll/.dylib).
 * Extracts from JAR resources to temp directory and loads via System.load().
 */
public final class CrestRenderLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("CrestRender");
    
    private static volatile boolean loaded = false;
    private static volatile boolean failed = false;
    
    private CrestRenderLoader() {}
    
    public static boolean isLoaded() {
        return loaded;
    }
    
    public static boolean isFailed() {
        return failed;
    }
    
    public static synchronized void ensureLoaded() {
        if (loaded || failed) {
            return;
        }
        
        String os = normalizeOs(System.getProperty("os.name", ""));
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        String libName = getLibraryName(os);
        
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "crest-client", "crestrender", os, arch);
        
        try {
            Files.createDirectories(tempDir);
            Path libPath = tempDir.resolve(libName);
            
            String resourcePath = "/natives/" + os + "/" + arch + "/" + libName;
            extractLibrary(resourcePath, libPath);
            
            System.load(libPath.toAbsolutePath().toString());
            
            boolean initResult = CrestRenderNative.nativeInit();
            if (!initResult) {
                throw new RuntimeException("nativeInit() returned false");
            }
            
            String version = CrestRenderNative.nativeGetVersion();
            LOGGER.info("[CrestRender] Loaded {} from {}", version, libPath);
            
            loaded = true;
            
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[CrestRender] Failed to load native library", t);
        }
    }
    
    private static void extractLibrary(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            LOGGER.debug("Native library already extracted: {}", target);
            return;
        }
        
        try (InputStream in = CrestRenderLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Native library not bundled in JAR: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().setExecutable(true, false);
            LOGGER.info("Extracted native library: {}", target);
        }
    }
    
    private static String normalizeOs(String os) {
        String lower = os.toLowerCase();
        if (lower.contains("win")) return "windows";
        if (lower.contains("mac")) return "macos";
        if (lower.contains("linux")) return "linux";
        return lower.replaceAll("[^a-z0-9_.-]", "");
    }
    
    private static String normalizeArch(String arch) {
        String lower = arch.toLowerCase();
        if (lower.matches("^(x8664|amd64|ia32e|em64t|x64)$")) return "x86_64";
        if (lower.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) return "x86";
        if (lower.equals("aarch64") || lower.equals("arm64")) return "arm64";
        if (lower.equals("arm") || lower.matches("^armv[0-9].*")) return "arm";
        return lower;
    }
    
    private static String getLibraryName(String os) {
        switch (os) {
            case "windows": return "crestrender.dll";
            case "macos": return "libcrestrender.dylib";
            default: return "libcrestrender.so";
        }
    }
}
