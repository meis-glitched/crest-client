package com.crest.client.music;

import com.crest.client.core.concurrent.CrestExecutors;
import com.crest.client.core.logging.CrestLogger;
import com.crest.client.core.logging.LoggerFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class MusicPlayer {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("MusicPlayer");
    public enum RepeatMode { OFF, ONE, ALL }

    private final AudioPlayerManager manager;
    private final AudioPlayer player;

    private volatile AudioTrack currentTrack;
    private volatile AudioPlaylist currentPlaylist;
    private volatile List<AudioTrack> queue = new ArrayList<>();
    private volatile int queueIndex = -1;
    private volatile boolean paused;
    private volatile float sliderVolume = 100f;
    // Extra multiplier applied on top of the slider volume (used for ducking);
    // never persisted, so ducking never clobbers the user's actual volume.
    private volatile float volumeFactor = 1f;

    private volatile RepeatMode repeatMode = RepeatMode.OFF;
    private volatile boolean shuffle = false;

    private Thread playbackThread;
    private final Object pauseLock = new Object();

    // Single persistent playback thread lives for the life of the player and
    // simply keeps pulling frames; track changes are sequenced by Lavaplayer
    // itself (onTrackEnd advances the queue), so we never spawn/kill per track.
    private volatile boolean running = true;
    private boolean outputOpen = false;

    private Process audioProcess;
    private OutputStream audioOutput;
    private String audioBackend;
    private String audioBackendPath;
    private volatile boolean backendAvailable;
    private final Object outputLock = new Object();

    private enum OutputKind { JAVASOUND, SYSTEM, NONE }
    private OutputKind outputKind = OutputKind.NONE;
    private final JavaSoundOutput javaSound = new JavaSoundOutput();
    private int outChannels = 2;
    private int outRate = 48000;

    private volatile OnStateChange onTrackStart;
    private volatile OnStateChange onTrackEnd;
    private volatile OnStateChange onQueueChange;
    private volatile OnError onError;

    public interface OnStateChange {
        void run(MusicPlayer mp);
    }
    public interface OnError {
        void run(String msg);
    }
    public interface SearchCallback {
        void onResults(List<AudioTrack> tracks);
        void onError(String msg);
    }

    public MusicPlayer() {
        manager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(manager);
        AudioSourceManagers.registerLocalSource(manager);

        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        player = manager.createPlayer();
        player.setFrameBufferDuration(500);
        player.addListener(new AudioEventAdapter() {
            @Override
            public void onPlayerPause(AudioPlayer p) {
                paused = true;
            }

            @Override
            public void onPlayerResume(AudioPlayer p) {
                paused = false;
                synchronized (pauseLock) { pauseLock.notifyAll(); }
            }

            @Override
            public void onTrackStart(AudioPlayer p, AudioTrack track) {
                LOGGER.audio("Track started: {} by {}", track.getInfo().title, track.getInfo().author);
                currentTrack = track;
                paused = false;
                startLoop();
                if (onTrackStart != null) onTrackStart.run(MusicPlayer.this);
            }

            @Override
            public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason reason) {
                LOGGER.audio("Track ended: {}", reason);
                currentTrack = null;
                if (reason == AudioTrackEndReason.FINISHED) {
                    // Auto-advance unless we're on repeat-one (handled below) or stopped.
                    if (repeatMode == RepeatMode.ONE && queueIndex >= 0) {
                        player.startTrack(queue.get(queueIndex).makeClone(), false);
                    } else {
                        boolean hadNext = next();
                        if (!hadNext && onTrackEnd != null) onTrackEnd.run(MusicPlayer.this);
                    }
                } else if (onTrackEnd != null) {
                    onTrackEnd.run(MusicPlayer.this);
                }
            }

            @Override
            public void onTrackException(AudioPlayer p, AudioTrack track, FriendlyException ex) {
                LOGGER.error("Error playing track: {}", ex, track.getInfo().title);
                if (onError != null) onError.run(ex.getMessage());
            }

            @Override
            public void onTrackStuck(AudioPlayer p, AudioTrack track, long thresholdMs) {
                LOGGER.error("Track stuck: {} (threshold: {}ms)", track.getInfo().title, thresholdMs);
                if (onError != null) onError.run("Track stuck: " + track.getInfo().title);
            }
        });

        selectBackend();
        startLoop();
    }

    private void selectBackend() {
        LOGGER.info("Selecting audio backend...");
        // 1) Self-contained JDK audio (javax.sound.sampled) — no system CLI, no
        //    separate OpenAL context. SourceDataLine.write() blocks to the device
        //    buffer and naturally paces playback to real time.
        if (javaSound.init()) {
            outputKind = OutputKind.JAVASOUND;
            audioBackend = "javasound";
            audioBackendPath = null;
            backendAvailable = true;
            LOGGER.info("Using JavaSound audio backend");
            LOGGER.info("Audio backend: JavaSound (self-contained)");
            return;
        }

        // 2) Fallback: system audio CLI (pulseaudio / pipewire / alsa). LavaPlayer's
        //    COMMON_PCM_S16_LE output is 48000 Hz, so the raw stream must be 48k.
        String[] backends = {"paplay", "pw-play", "aplay", "ffplay"};
        String[][] args = {
            {"paplay", "--raw", "--rate=48000", "--channels=2", "--format=s16le"},
            {"pw-play", "--raw", "--rate=48000", "--channels=2", "--format=s16le"},
            {"aplay", "-f", "S16_LE", "-r", "48000", "-c", "2"},
            {"ffplay", "-f", "s16le", "-ar", "48000", "-ac", "2", "-nodisp", "-autoexit", "-"}
        };
        String[] candidates = {
            "/usr/bin/paplay", "/bin/paplay",
            "/usr/bin/pw-play", "/bin/pw-play",
            "/usr/bin/aplay", "/bin/aplay",
            "/usr/bin/ffplay", "/bin/ffplay"
        };
        for (int i = 0; i < backends.length; i++) {
            try {
                // Only run binaries from known absolute system locations to avoid
                // executing attacker-controlled binaries from the ambient PATH.
                String resolved = null;
                for (String c : candidates) {
                    if (c.endsWith("/" + backends[i]) && new java.io.File(c).canExecute()) {
                        resolved = c;
                        break;
                    }
                }
                if (resolved == null) continue;
                Process p = new ProcessBuilder(resolved, args[i][1], args[i][2], args[i][3], args[i][4], args[i][5]).start();
                if (p.isAlive()) {
                    p.destroy();
                    outputKind = OutputKind.SYSTEM;
                    audioBackend = backends[i];
                    audioBackendPath = resolved;
                    backendAvailable = true;
                    LOGGER.info("Using audio backend: {} ({})", audioBackend, resolved);
                    return;
                }
            } catch (Exception e) {
                // try next backend
            }
        }
        LOGGER.error("No audio backend found!");
        outputKind = OutputKind.NONE;
        audioBackend = null;
        audioBackendPath = null;
        backendAvailable = false;
    }

    public boolean isBackendAvailable() { return backendAvailable; }

    public static boolean isUrlAllowed(String url) {
        return com.crest.client.core.security.UrlValidator.isUrlAllowed(url);
    }

    public void search(String query, SearchCallback callback) {
        if (query == null || query.isBlank()) return;
        String sq = "scsearch:" + query;
        LOGGER.info("Searching: {}", sq);
        manager.loadItem(sq, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                callback.onResults(List.of(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                callback.onResults(playlist.getTracks());
            }

            @Override
            public void noMatches() {
                callback.onError("No results found for \"" + query + "\"");
            }

            @Override
            public void loadFailed(FriendlyException ex) {
                callback.onError("Search failed: " + ex.getMessage());
            }
        });
    }

    public void loadAndPlay(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.contains("://") || !isUrlAllowed(url)) {
            LOGGER.security("Refused to load disallowed URL: {}", url);
            if (onError != null) onError.run("URL not allowed: " + url);
            return;
        }
        LOGGER.info("Loading: {}", url);
        manager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                LOGGER.info("Track loaded: {}", track.getInfo().title);
                playList(List.of(track), 0);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    LOGGER.info("Playlist is empty");
                    if (onError != null) onError.run("Playlist is empty");
                    return;
                }
                LOGGER.info("Playlist loaded: {} tracks", playlist.getTracks().size());
                currentPlaylist = playlist;
                playList(playlist.getTracks(), 0);
            }

            @Override
            public void noMatches() {
                LOGGER.info("No matches for: {}", url);
                if (onError != null) onError.run("No results for URL");
            }

            @Override
            public void loadFailed(FriendlyException ex) {
                LOGGER.error("Load failed for {}", ex, url);
                if (onError != null) onError.run("Load failed: " + ex.getMessage());
            }
        });
    }

    /** Load all playable audio files from a local folder (absolute path). */
    public void loadLocalFolder(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) return;
        java.io.File dir = new java.io.File(folderPath);
        if (!dir.isDirectory()) {
            if (onError != null) onError.run("Not a folder: " + folderPath);
            return;
        }
        LOGGER.info("Loading folder: {}", folderPath);
        manager.loadItem("folder://" + folderPath, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                playList(List.of(track), 0);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    if (onError != null) onError.run("No audio files found in folder");
                    return;
                }
                currentPlaylist = playlist;
                playList(playlist.getTracks(), 0);
            }

            @Override
            public void noMatches() {
                if (onError != null) onError.run("No audio files found in folder");
            }

            @Override
            public void loadFailed(FriendlyException ex) {
                if (onError != null) onError.run("Folder load failed: " + ex.getMessage());
            }
        });
    }

    /** Replace the queue and start playing at the given index. */
    public void playList(List<AudioTrack> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        player.stopTrack();
        currentPlaylist = null;
        List<AudioTrack> list = new ArrayList<>(tracks);
        if (shuffle) shuffleQueue(list);
        queue = list;
        queueIndex = Math.max(0, Math.min(startIndex, list.size() - 1));
        player.startTrack(list.get(queueIndex), false);
        notifyQueueChange();
    }

    /** Append tracks to the end of the queue (does not interrupt current playback). */
    public void enqueue(List<AudioTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        List<AudioTrack> list = new ArrayList<>(queue);
        if (list.isEmpty()) {
            playList(tracks, 0);
            return;
        }
        list.addAll(tracks);
        queue = list;
        notifyQueueChange();
    }

    private void shuffleQueue(List<AudioTrack> list) {
        if (list.size() <= 1) return;
        Random r = new Random();
        for (int i = list.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            AudioTrack t = list.get(i); list.set(i, list.get(j)); list.set(j, t);
        }
    }

    /** Advance to the next track in the queue. Returns false if nothing to play. */
    public boolean next() {
        if (queue.isEmpty()) return false;
        int n = queueIndex + 1;
        if (n >= queue.size()) {
            if (repeatMode == RepeatMode.ALL) n = 0;
            else return false;
        }
        queueIndex = n;
        player.startTrack(queue.get(queueIndex), false);
        notifyQueueChange();
        return true;
    }

    /** Go back to the previous track in the queue. Returns false if at start. */
    public boolean previous() {
        if (queue.isEmpty()) return false;
        int p = queueIndex - 1;
        if (p < 0) {
            if (repeatMode == RepeatMode.ALL) p = queue.size() - 1;
            else return false;
        }
        queueIndex = p;
        player.startTrack(queue.get(queueIndex), false);
        notifyQueueChange();
        return true;
    }

    /** Jump directly to a queue index and play it. */
    public void playQueueItem(int index) {
        if (index < 0 || index >= queue.size()) return;
        queueIndex = index;
        player.startTrack(queue.get(queueIndex), false);
        notifyQueueChange();
    }

    public List<AudioTrack> getQueue() { return queue; }
    public int getQueueIndex() { return queueIndex; }

    public void setRepeatMode(RepeatMode mode) { repeatMode = mode; }
    public RepeatMode getRepeatMode() { return repeatMode; }
    public void cycleRepeatMode() {
        repeatMode = switch (repeatMode) {
            case OFF -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.OFF;
        };
    }

    public void setShuffle(boolean s) {
        if (s == shuffle) return;
        shuffle = s;
        if (s && !queue.isEmpty()) {
            // Reshuffle keeping the current track first.
            AudioTrack cur = queueIndex >= 0 && queueIndex < queue.size() ? queue.get(queueIndex) : null;
            List<AudioTrack> rest = new ArrayList<>(queue);
            if (cur != null) rest.remove(cur);
            shuffleQueue(rest);
            if (cur != null) rest.add(0, cur);
            queue = rest;
            queueIndex = cur != null ? 0 : queueIndex;
            notifyQueueChange();
        }
    }
    public boolean isShuffle() { return shuffle; }

    private void notifyQueueChange() {
        if (onQueueChange != null) onQueueChange.run(this);
    }

    public void setOnQueueChange(OnStateChange cb) { onQueueChange = cb; }

    public void play() {
        if (currentTrack != null) {
            paused = false;
            player.setPaused(false);
        }
    }

    public void pause() {
        paused = true;
        player.setPaused(true);
    }

    public void stop() {
        paused = false;
        player.stopTrack();
        stopLoop();
        closeOutput();
    }

    public void togglePause() {
        if (currentTrack == null) return;
        if (paused) play();
        else pause();
    }

    public void seek(long positionMs) {
        if (currentTrack != null) {
            flushOutput();
            currentTrack.setPosition(positionMs);
        }
    }

    public boolean isPlaying() {
        return currentTrack != null && !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean hasTrack() {
        return currentTrack != null;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public AudioPlaylist getCurrentPlaylist() {
        return currentPlaylist;
    }

    public long getPosition() {
        return currentTrack != null ? currentTrack.getPosition() : 0;
    }

    public long getDuration() {
        return currentTrack != null ? currentTrack.getDuration() : 0;
    }

    public float getProgress() {
        long dur = getDuration();
        return dur > 0 ? (float) getPosition() / dur : 0;
    }

    public void setSliderVolume(float vol) {
        sliderVolume = Math.max(0, Math.min(100, vol));
        player.setVolume((int) (sliderVolume * volumeFactor));
    }

    public float getSliderVolume() {
        return sliderVolume;
    }

    /** Multiplier applied on top of {@link #getSliderVolume()} (ducking). */
    public void setVolumeFactor(float factor) {
        volumeFactor = Math.max(0f, Math.min(1f, factor));
        player.setVolume((int) (sliderVolume * volumeFactor));
    }

    public float getVolumeFactor() {
        return volumeFactor;
    }

    public List<String> getQueueUris() {
        List<String> uris = new ArrayList<>(queue.size());
        for (AudioTrack t : queue) {
            if (t != null && t.getInfo() != null && t.getInfo().uri != null) uris.add(t.getInfo().uri);
        }
        return uris;
    }

    /**
     * Asynchronously re-load a previously saved queue and start playing at
     * {@code startIndex}. No-ops if audio is already loaded/playing, so a fresh
     * manual load can never be clobbered. Failed/blocked URIs are skipped.
     */
    public void restoreQueue(List<String> uris, int startIndex) {
        if (uris == null || uris.isEmpty()) return;
        if (currentTrack != null || !queue.isEmpty()) return;

        int n = uris.size();
        final AudioTrack[] slots = new AudioTrack[n];
        final int[] pending = {n};
        for (int i = 0; i < n; i++) {
            String uri = uris.get(i);
            if (uri == null || uri.isBlank() || !isUrlAllowed(uri)) {
                synchronized (pending) { finishRestore(slots, pending, startIndex); }
                continue;
            }
            final int index = i;
            try {
                manager.loadItem(uri, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        slots[index] = track.makeClone();
                        finishRestore(slots, pending, startIndex);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        if (!playlist.getTracks().isEmpty()) {
                            slots[index] = playlist.getTracks().get(0).makeClone();
                        }
                        finishRestore(slots, pending, startIndex);
                    }

                    @Override
                    public void noMatches() {
                        finishRestore(slots, pending, startIndex);
                    }

                    @Override
                    public void loadFailed(FriendlyException ex) {
                        LOGGER.error("Restore failed for {}", ex, uri);
                        finishRestore(slots, pending, startIndex);
                    }
                });
            } catch (Exception e) {
                synchronized (pending) { finishRestore(slots, pending, startIndex); }
            }
        }
    }

    private void finishRestore(AudioTrack[] slots, int[] pending, int startIndex) {
        if (--pending[0] > 0) return;
        // Only apply if nothing else has started in the meantime.
        if (currentTrack != null || !queue.isEmpty()) return;
        List<AudioTrack> tracks = new ArrayList<>();
        for (AudioTrack t : slots) {
            if (t != null) tracks.add(t);
        }
        if (tracks.isEmpty()) {
            LOGGER.info("Nothing restored from saved queue");
            return;
        }
        LOGGER.info("Restoring {} queued tracks", tracks.size());
        playList(tracks, Math.max(0, Math.min(startIndex, tracks.size() - 1)));
    }

    public void setOnTrackStart(OnStateChange cb) { onTrackStart = cb; }
    public void setOnTrackEnd(OnStateChange cb) { onTrackEnd = cb; }
    public void setOnError(OnError cb) { onError = cb; }

    public void destroy() {
        stop();
        javaSound.destroy();
        player.destroy();
    }

    private void startLoop() {
        // If a healthy loop is already running, do nothing. Otherwise (fresh start
        // or restart after stop) spawn a new one. We never join here so we don't
        // block the Lavaplayer audio thread.
        if (playbackThread != null && playbackThread.isAlive() && running) return;
        running = true;
        playbackThread = new Thread(this::playbackLoop, "crest-music-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void stopLoop() {
        running = false;
        Thread t = playbackThread;
        if (t != null) t.interrupt();
    }

    private boolean openOutput() {
        synchronized (outputLock) {
            closeOutput();
            if (outputKind == OutputKind.JAVASOUND) {
                outputOpen = javaSound.open();
                return outputOpen;
            } else if (outputKind == OutputKind.SYSTEM) {
                if (audioBackendPath == null) {
                    LOGGER.error("No audio backend available");
                    return false;
                }
                try {
                    String[][] cmds = {
                        {"--raw", "--rate=48000", "--channels=2", "--format=s16le"},
                        {"--raw", "--rate=48000", "--channels=2", "--format=s16le"},
                        {"-f", "S16_LE", "-r", "48000", "-c", "2"},
                        {"-f", "s16le", "-ar", "48000", "-ac", "2", "-nodisp", "-autoexit", "-"}
                    };
                    int idx = switch (audioBackend) {
                        case "paplay" -> 0;
                        case "pw-play" -> 1;
                        case "aplay" -> 2;
                        case "ffplay" -> 3;
                        default -> -1;
                    };
                    if (idx < 0) return false;
                    String[] base = {audioBackendPath, cmds[idx][0], cmds[idx][1], cmds[idx][2], cmds[idx][3]};
                    ProcessBuilder pb = new ProcessBuilder(base);
                    pb.redirectErrorStream(false);
                    audioProcess = pb.start();
                    audioOutput = audioProcess.getOutputStream();
                    LOGGER.info("Audio output opened via {}", audioBackend);
                    outputOpen = true;
                    return true;
                } catch (Exception e) {
                    LOGGER.error("Failed to open audio output", e);
                    return false;
                }
            }
            return false;
        }
    }

    private void writeOutput(byte[] data) {
        if (data == null || data.length == 0) return;
        synchronized (outputLock) {
            if (outputKind == OutputKind.JAVASOUND) {
                javaSound.write(data, outChannels, outRate);
            } else if (outputKind == OutputKind.SYSTEM && audioOutput != null) {
                try {
                    audioOutput.write(data);
                } catch (Exception e) {
                    if (!Thread.interrupted()) {
                        LOGGER.error("Write error", e);
                    }
                }
            }
        }
    }

    private void closeOutput() {
        synchronized (outputLock) {
            if (outputKind == OutputKind.JAVASOUND) {
                javaSound.close();
            } else {
                try {
                    if (audioOutput != null) {
                        audioOutput.flush();
                        audioOutput.close();
                    }
                } catch (Exception e) { }
                audioOutput = null;
                if (audioProcess != null) {
                    try { audioProcess.destroy(); } catch (Exception e) { }
                    audioProcess = null;
                }
            }
            outputOpen = false;
        }
    }

    private void flushOutput() {
        synchronized (outputLock) {
            if (outputKind == OutputKind.JAVASOUND) javaSound.flush();
        }
    }

    private void playbackLoop() {
        int frameCount = 0;
        int nullCount = 0;
        long lastLog = 0;
        try {
            long nextFrameTime = 0; // real-time pacer: target wall-clock time for the next frame
            while (running && !Thread.interrupted()) {
                if (player.isPaused()) {
                    synchronized (pauseLock) {
                        try { pauseLock.wait(100); } catch (InterruptedException e) { break; }
                    }
                    // Reset pacer so resuming doesn't try to "catch up" the gap.
                    nextFrameTime = 0;
                    continue;
                }

                AudioFrame frame = player.provide();
                if (frame == null) {
                    nullCount++;
                    long now = System.currentTimeMillis();
                    if (now - lastLog > 2000) {
                        System.out.println("[Crest Music] provide() null x" + nullCount
                            + ", paused=" + player.isPaused()
                            + ", activeTrack=" + (player.getPlayingTrack() != null ? player.getPlayingTrack().getInfo().title : "null"));
                        lastLog = now;
                    }
                    try { Thread.sleep(5); } catch (InterruptedException e) { break; }
                    continue;
                }

                // A track ended; onTrackEnd has already sequenced the next one (or
                // not). Keep the loop alive and just wait for the next frame.
                if (frame.isTerminator()) {
                    try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                    continue;
                }

                byte[] data = frame.getData();
                if (data == null || data.length == 0) continue;

                // Open the output lazily on first real audio (don't grab the
                // device until something actually plays).
                if (!outputOpen && !openOutput()) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                AudioDataFormat fmt = frame.getFormat();
                if (fmt != null) {
                    outChannels = fmt.channelCount;
                    outRate = fmt.sampleRate;
                    if (frameCount == 0) {
                        System.out.println("[Crest Music] First frame: " + data.length + " bytes, format="
                            + fmt.channelCount + "ch " + fmt.sampleRate + "Hz "
                            + fmt.codecName() + " chunk=" + fmt.chunkSampleCount);
                    }
                }
                frameCount++;
                writeOutput(data);

                // Pace consumption to real time so the player's track-completion
                // and progress stay in sync with actual audio. Each frame is
                // chunkSampleCount / sampleRate seconds of audio; sleep until that
                // much wall-clock time has elapsed since the previous frame.
                double frameMs = (fmt != null && fmt.sampleRate > 0)
                    ? (fmt.chunkSampleCount * 1000.0 / fmt.sampleRate)
                    : 20.0;
                long now = System.currentTimeMillis();
                if (nextFrameTime == 0) {
                    nextFrameTime = now + (long) frameMs;
                } else {
                    long sleep = nextFrameTime - now;
                    if (sleep > 0) {
                        try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
                    }
                    nextFrameTime += (long) frameMs;
                    // Avoid runaway drift if we ever fell behind.
                    if (nextFrameTime < System.currentTimeMillis()) {
                        nextFrameTime = System.currentTimeMillis() + (long) frameMs;
                    }
                }

                if (frameCount % 100 == 1 && frameCount > 1) {
                    long pos = currentTrack != null ? currentTrack.getPosition() : 0;
                    System.out.println("[Crest Music] Frames: " + frameCount + ", pos=" + pos + "ms");
                }
            }
        } catch (Exception e) {
            if (!(e instanceof InterruptedException)) {
                System.err.println("[Crest Music] Playback error: " + e);
                e.printStackTrace();
            }
        } finally {
            System.out.println("[Crest Music] Playback thread ending, frames played=" + frameCount);
        }
    }
}
