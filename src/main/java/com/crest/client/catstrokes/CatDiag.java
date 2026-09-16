package com.crest.client.catstrokes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal file-appended diagnostics for the Bongo Cat pipeline. Writes to
 * {@code /tmp/catstrokes/catstrokes.log}. Used because SLF4J routing under the
 * dev launcher proved unreliable for this module's logger name.
 */
public final class CatDiag {
    private static final PrintStream OUT = open();

    private CatDiag() {}

    private static PrintStream open() {
        try {
            File dir = new File("/tmp/catstrokes");
            dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(new File(dir, "catstrokes.log"), true);
            return new PrintStream(fos, true, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return System.err;
        }
    }

    public static void log(String msg) {
        String line = "[" + java.time.LocalTime.now() + "] " + Thread.currentThread().getName() + " " + msg;
        OUT.println(line);
        System.err.println(line);
    }

    public static void log(Throwable t, String msg) {
        log(msg);
        t.printStackTrace(OUT);
        t.printStackTrace(System.err);
    }
}