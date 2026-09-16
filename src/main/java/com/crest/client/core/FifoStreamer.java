package com.crest.client.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;

public class FifoStreamer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("FifoStreamer");
    private static final Path VIDEO_FIFO = Path.of("/tmp/crest_video_fifo");

    private static VideoEncoderThread encoderThread;
    private static FileChannel fifoChannel;
    private static Process ffmpegProcess;
    private static Thread errorMonitor;

    public static void startStream(String url, int fps, int width, int height, int bitrate,
                                    String encoder, String preset, String audioDevice,
                                    double scaleFactor, String recordOutput) throws IOException {
        boolean audioRequested = audioDevice != null && !audioDevice.isEmpty() && !audioDevice.equals("none");

        // Create the video FIFO before launching ffmpeg so it has a reader to
        // attach to. Recreate it fresh each start.
        cleanupFifos();
        mkfifo(VIDEO_FIFO);

        List<String> args = buildFfmpegArgs(url, fps, width, height, bitrate, true, encoder, preset, audioRequested ? audioDevice : "none", scaleFactor, recordOutput);
        ffmpegProcess = launchFfmpeg(args);

        // ponytail: if audio was requested, give ffmpeg a moment to actually open
        // the stream. If it dies (bad device / no sound server), fall back to a
        // no-audio stream instead of failing silently.
        if (audioRequested && ffmpegDiedEarly()) {
            stop();
            Streamer.setLastError("Audio device unavailable — streaming without audio");
            Streamer.setAudioFellBack(true);
            audioRequested = false;
            cleanupFifos();
            mkfifo(VIDEO_FIFO);
            args = buildFfmpegArgs(url, fps, width, height, bitrate, true, encoder, preset, "none", scaleFactor, recordOutput);
            ffmpegProcess = launchFfmpeg(args);
        }

        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Opening a FIFO for write blocks until a reader (ffmpeg) attaches. We
        // already slept ~500ms after launch above, but retry defensively in case
        // ffmpeg is still initializing.
        fifoChannel = null;
        for (int i = 0; i < 100 && fifoChannel == null; i++) {
            try {
                fifoChannel = FileChannel.open(VIDEO_FIFO, StandardOpenOption.WRITE);
            } catch (IOException e) {
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (fifoChannel == null) {
            throw new IOException("Timed out opening video FIFO for writing");
        }

        FrameCapture.start(fps, width, height, true);

        encoderThread = new VideoEncoderThread(
            FrameCapture.getFilledQueue(),
            FrameCapture.getFreePool(),
            fifoChannel
        );
        encoderThread.start();
    }

    // ponytail: returns true if the ffmpeg process exited within a short window
    // after launch (indicates it failed to start, e.g. bad audio device).
    private static boolean ffmpegDiedEarly() {
        for (int i = 0; i < 10; i++) {
            if (ffmpegProcess != null && !ffmpegProcess.isAlive()) return true;
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private static int scaled(int dim, double factor) {
        int s = (int) Math.round(dim * factor);
        if (s % 2 != 0) s--; // must be even for chroma subsampling
        return Math.max(2, s);
    }

    public static void stop() {
        FrameCapture.stop();

        if (encoderThread != null) {
            encoderThread.shutdown();
            try { encoderThread.join(3000); } catch (InterruptedException ignored) {}
            encoderThread = null;
        }

        if (fifoChannel != null) {
            try { fifoChannel.close(); } catch (IOException ignored) {}
            fifoChannel = null;
        }

        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            try { ffmpegProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (ffmpegProcess.isAlive()) ffmpegProcess.destroyForcibly();
            ffmpegProcess = null;
        }

        if (errorMonitor != null) {
            try { errorMonitor.join(2000); } catch (InterruptedException ignored) {}
            errorMonitor = null;
        }

        cleanupFifos();
    }

    private static void mkfifo(Path path) throws IOException {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"mkfifo", path.toString()});
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Block until the FIFO node actually exists before we try to open it.
        for (int i = 0; i < 50; i++) {
            if (Files.exists(path)) return;
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new IOException("mkfifo failed to create " + path);
    }

    private static void cleanupFifos() {
        try { Files.deleteIfExists(VIDEO_FIFO); } catch (IOException ignored) {}
    }

    private static Process launchFfmpeg(List<String> args) throws IOException {
        StringJoiner sj = new StringJoiner(" ");
        for (String a : args) sj.add(a.contains(" ") ? "'" + a + "'" : a);
        LOGGER.info("ffmpeg command: {}", sj);
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        errorMonitor = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[ffmpeg] " + line);
                }
            } catch (Exception ignored) {}
        }, "ffmpeg-stderr");
        errorMonitor.setDaemon(true);
        errorMonitor.start();
        return p;
    }

    private static List<String> buildFfmpegArgs(String output, int fps, int width, int height,
                                                 int bitrate, boolean streaming,
                                                 String encoder, String preset, String audioDevice,
                                                 double scaleFactor, String recordOutput) {
        // Use the REAL captured dimensions (render target), which can differ from
        // the window size on HiDPI/scaled displays; otherwise frames are garbled.
        int capW = FrameCapture.getCapturedWidth();
        int capH = FrameCapture.getCapturedHeight();
        if (capW > 0 && capH > 0) { width = capW; height = capH; }

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-f"); cmd.add("rawvideo");
        cmd.add("-pix_fmt"); cmd.add("rgba");
        cmd.add("-s"); cmd.add(width + "x" + height);
        cmd.add("-thread_queue_size"); cmd.add("512");
        cmd.add("-r"); cmd.add(String.valueOf(fps));
        cmd.add("-i"); cmd.add(VIDEO_FIFO.toString());

        // Audio input: real device or silence
        if (audioDevice != null && !audioDevice.isEmpty() && !audioDevice.equals("none")) {
            String backend = EncoderProbe.getWorkingAudioBackend();
            cmd.add("-f"); cmd.add(backend);
            cmd.add("-i"); cmd.add(audioDevice);
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("64k");
            cmd.add("-ar"); cmd.add("44100");
            cmd.add("-ac"); cmd.add("2");
        } else {
            cmd.add("-f"); cmd.add("lavfi");
            cmd.add("-i"); cmd.add("anullsrc=r=44100:cl=stereo");
            cmd.add("-c:a"); cmd.add("aac");
            cmd.add("-b:a"); cmd.add("64k");
            cmd.add("-ar"); cmd.add("44100");
            cmd.add("-ac"); cmd.add("2");
        }

        cmd.add("-c:v"); cmd.add(encoder);

        // Force constant-frame-rate output timestamps from the declared fps so
        // the stream plays back at the correct speed (rawvideo has no PTS).
        cmd.add("-fps_mode"); cmd.add("cfr");

        boolean isVaapi = EncoderProbe.isVaapi(encoder);

        if (isVaapi) {
            // VAAPI needs the device and the hwupload filter
            String dev = EncoderProbe.getVaapiDevice();
            if (dev != null) {
                cmd.add("-vaapi_device"); cmd.add(dev);
            }
        }

        if (encoder.startsWith("libx") || encoder.startsWith("software")) {
            cmd.add("-preset"); cmd.add(preset);
            // ponytail: low-latency tune for software encoding
            cmd.add("-tune"); cmd.add("zerolatency");
            cmd.add("-bf"); cmd.add("0");
            cmd.add("-refs"); cmd.add("1");
        } else if (encoder.equals("h264_qsv") || encoder.equals("hevc_qsv")) {
            cmd.add("-preset"); cmd.add(preset);
        } else if (encoder.endsWith("_nvenc") || encoder.endsWith("_amf") || encoder.endsWith("_videotoolbox")) {
            cmd.add("-preset"); cmd.add(preset);
        } else if (isVaapi) {
            // VAAPI has no -preset option (silently ignored). Map the speed
            // selection to the real controls: -quality (higher = faster encode),
            // encoder parallelism, and VBR rate control for streaming.
            int quality;
            if ("fast".equals(preset)) quality = 8;
            else if ("slow".equals(preset)) quality = 2;
            else quality = 5;
            cmd.add("-quality"); cmd.add(String.valueOf(quality));
            cmd.add("-async_depth"); cmd.add("8");
            cmd.add("-rc_mode"); cmd.add("3");
        }

        // Build filter chain: scale (if needed) + vflip + format
        int sw = scaled(width, scaleFactor);
        int sh = scaled(height, scaleFactor);
        StringBuilder vf = new StringBuilder();
        if (isVaapi) {
            // VAAPI pipeline: software filters (vflip/format) must run BEFORE the
            // frames are uploaded to the GPU. hwupload first then vflip/format makes
            // the graph fail with "Invalid argument" and kills the stream. Scaling
            // happens on the GPU afterwards with scale_vaapi.
            vf.append("format=nv12,vflip,hwupload");
            if (scaleFactor < 0.99 || scaleFactor > 1.01) {
                vf.append(",scale_vaapi=w=").append(sw).append(":h=").append(sh);
            }
        } else {
            if (scaleFactor < 0.99 || scaleFactor > 1.01) {
                vf.append("scale=").append(sw).append(":").append(sh).append(",");
            }
            vf.append("vflip,format=nv12");
        }
        cmd.add("-vf"); cmd.add(vf.toString());

        if (streaming && scaleFactor < 0.99) {
            int adjustedBitrate = (int) (bitrate * scaleFactor * scaleFactor);
            adjustedBitrate = Math.max(500, adjustedBitrate);
            cmd.add("-b:v"); cmd.add(adjustedBitrate + "k");
            cmd.add("-maxrate"); cmd.add(adjustedBitrate + "k");
            cmd.add("-bufsize"); cmd.add((adjustedBitrate * 2) + "k");
        } else if (streaming) {
            cmd.add("-b:v"); cmd.add(bitrate + "k");
            cmd.add("-maxrate"); cmd.add(bitrate + "k");
            cmd.add("-bufsize"); cmd.add((bitrate * 2) + "k");
        } else {
            cmd.add("-qp"); cmd.add("23");
        }

        cmd.add("-g"); cmd.add(String.valueOf(fps * 2));

        // Map video (input 0) and audio (input 1) explicitly so audio is present
        // in the output regardless of stream/record branching.
        cmd.add("-map"); cmd.add("0:v:0");
        cmd.add("-map"); cmd.add("1:a:0");

        // Dual output: stream + record via tee muxer
        boolean dualOutput = streaming && recordOutput != null && !recordOutput.isEmpty();
        if (dualOutput) {
            cmd.add("-f"); cmd.add("tee");
            cmd.add("-flags"); cmd.add("+global_header");
            String teeSpec = "[f=flv:onfail=ignore]" + output + "|[f=matroska:onfail=ignore]" + recordOutput;
            cmd.add(teeSpec);
        } else if (streaming) {
            cmd.add("-shortest");
            cmd.add("-f"); cmd.add("flv");
            cmd.add(output);
        } else {
            cmd.add("-shortest");
            cmd.add("-f"); cmd.add("matroska");
            cmd.add(recordOutput);
        }
        return cmd;
    }
}
