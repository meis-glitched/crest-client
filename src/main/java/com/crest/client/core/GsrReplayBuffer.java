package com.crest.client.core;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Instant replay via gpu-screen-recorder's native replay buffer. Runs a separate
// GSR process in replay mode (-c mp4 -r <sec> -o <dir> -ipc <sock>) and saves the
// clip on demand with gsr-cli, which returns the saved file path. Keeps the same
// static API as ReplayBuffer so ReplayBufferModule can route between backends.
public class GsrReplayBuffer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("GsrReplay");

    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static volatile int durationSec;
    private static volatile Process process;
    private static volatile String socketPath;
    private static final AtomicBoolean saving = new AtomicBoolean(false);

    public static boolean isActive() { return active.get(); }
    public static boolean isSaving() { return saving.get(); }
    public static int getDurationSec() { return durationSec; }

    public static void start(int sourceFps, int duration) {
        if (active.getAndSet(true)) return;
        durationSec = duration;

        String monitor = MonitorUtil.getFocusedMonitor();
        if (monitor == null) {
            LOGGER.warn("Could not determine monitor for replay buffer");
            active.set(false);
            return;
        }

        String dir = System.getProperty("user.home") + "/Videos/";
        new File(dir).mkdirs();

        socketPath = System.getProperty("java.io.tmpdir")
            + "/crest-replay-" + ProcessHandle.current().pid() + ".sock";
        new File(socketPath).delete();

        List<String> cmd = new ArrayList<>();
        cmd.add("gpu-screen-recorder");
        cmd.add("-w"); cmd.add(monitor);
        cmd.add("-f"); cmd.add(String.valueOf(Math.max(20, sourceFps)));
        cmd.add("-fm"); cmd.add("cfr");
        cmd.add("-k"); cmd.add("h264");
        cmd.add("-bm"); cmd.add("cbr");
        cmd.add("-q"); cmd.add("10000");
        cmd.add("-c"); cmd.add("mp4");
        cmd.add("-r"); cmd.add(String.valueOf(Math.max(2, duration)));
        cmd.add("-replay-storage"); cmd.add("ram");
        cmd.add("-a"); cmd.add("default_output");
        cmd.add("-ac"); cmd.add("aac");
        cmd.add("-o"); cmd.add(dir);
        cmd.add("-ipc"); cmd.add(socketPath);

        LOGGER.info("Starting gpu-screen-recorder replay buffer ({}s) → {}", duration, dir);
        LOGGER.info("gpu-screen-recorder replay command: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            process = pb.start();
            monitorProcess(process);
        } catch (IOException e) {
            LOGGER.warn("Failed to launch replay buffer: {}", e.getMessage());
            active.set(false);
            process = null;
        }
    }

    public static void stop() {
        active.set(false);
        if (process != null && process.isAlive()) {
            long pid = process.pid();
            try {
                Runtime.getRuntime().exec(new String[]{"kill", "-INT", String.valueOf(pid)});
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (Exception ignored) {
                process.destroyForcibly();
            }
        }
        process = null;
        try {
            if (socketPath != null) new File(socketPath).delete();
        } catch (Exception ignored) {}
        socketPath = null;
    }

    // Signals GSR to save the whole replay buffer and shows a toast with the
    // resulting file. gsr-cli blocks until the clip has been encoded.
    public static void saveAsync() {
        if (!active.get() || process == null || !process.isAlive()) {
            NotificationToast.show("Replay buffer not running", 0xFFFFB74D);
            return;
        }
        if (saving.get()) return;

        Thread t = new Thread(() -> {
            saving.set(true);
            String path = null;
            try {
                Process p = new ProcessBuilder("gsr-cli", "-ipc", socketPath, "save-replay").start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    path = r.readLine();
                }
                p.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn("Replay save failed: {}", e.getMessage());
                path = null;
            } finally {
                saving.set(false);
            }
            if (path != null && !path.isEmpty()) {
                LOGGER.info("Saved replay clip → {}", path);
                NotificationToast.show("Clip saved: " + new File(path).getName());
            } else {
                NotificationToast.show("Could not save replay clip", 0xFFFF5B6E);
            }
        }, "gsr-replay-save");
        t.setDaemon(true);
        t.start();
    }

    private static void monitorProcess(Process p) {
        Thread t = new Thread(() -> relay(p, p.getInputStream()), "gsr-replay-relay");
        Thread u = new Thread(() -> relay(p, p.getErrorStream()), "gsr-replay-relay-err");
        t.setDaemon(true);
        u.setDaemon(true);
        t.start();
        u.start();
    }

    private static void relay(Process p, InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[gsr-replay] " + line);
            }
        } catch (Exception ignored) {}
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (active.compareAndSet(true, false)) {
            LOGGER.warn("Replay buffer process exited unexpectedly");
        }
    }
}