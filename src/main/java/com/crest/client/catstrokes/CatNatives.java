package com.crest.client.catstrokes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts and loads the two native libraries backing Bango Cat:
 * <ul>
 *   <li>{@code liblive2d_jni.so} - the Live2D framework binding used by
 *       {@link NativeCat}.</li>
 *   <li>{@code libbongocatjni.so} - JNI glue exposing the framework API as
 *       static native methods.</li>
 * </ul>
 * Both are bundled under the mod jar at {@code /natives/<os>/<arch>/<libname>}
 * (mirroring the existing {@code native} layout) and copied into the per-user
 * temp directory before being loaded. Once loaded they are cached forever.
 */
public final class CatNatives {
    private static volatile boolean loaded;
    private static volatile boolean failed;

    private static final Logger LOGGER = LoggerFactory.getLogger("Crest/CatStrokes");

    private static final String[] LIBS = {"libbongocatjni.so", "liblive2d_jni.so"};

    private CatNatives() {}

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
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "crest-client", "catnatives", os, arch);
        try {
            Files.createDirectories(dir);
            for (String lib : LIBS) {
                Path out = dir.resolve(lib);
                String resource = "/natives/" + os + "/" + arch + "/" + lib;
                extract(resource, out);
            }
            Path main = dir.resolve("libbongocatjni.so");
            System.load(main.toAbsolutePath().toString());
            loaded = true;
            LOGGER.info("[BongoCat] natives loaded from {}", dir);
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[BongoCat] native load failed", t);
        }
    }

    private static void extract(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = CatNatives.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("native library not bundled: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().setExecutable(true, false);
        }
    }

    private static String normalizeOs(String os) {
        String o = os.toLowerCase();
        if (o.contains("win")) return "windows";
        if (o.contains("mac")) return "macos";
        if (o.contains("linux")) return "linux";
        return o.replaceAll("[^a-z0-9_.-]", "");
    }

    private static String normalizeArch(String arch) {
        String a = arch.toLowerCase();
        if (a.matches("^(x8664|amd64|ia32e|em64t|x64)$")) return "x86_64";
        if (a.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) return "x86";
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        if (a.equals("arm") || a.matches("^armv[0-9].*")) return "arm";
        return a;
    }
}