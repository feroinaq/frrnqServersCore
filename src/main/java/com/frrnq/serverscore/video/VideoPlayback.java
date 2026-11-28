package com.frrnq.serverscore.video;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;

/** One local playback session. Only copied pixels cross to the render thread. */
final class VideoPlayback implements VideoDecoder.VideoSink {
    private static final int QUEUE_SIZE = 8;
    private static final long VIDEO_LEAD_US = 220000;

    static final class Image {
        final long ptsUs;
        final int width;
        final int height;
        final int[] pixels;

        Image(long ptsUs, int width, int height, int[] pixels) {
            this.ptsUs = ptsUs;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private final ArrayBlockingQueue<Image> images = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final ArrayBlockingQueue<int[]> sparePixels = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private volatile boolean cancelled;
    volatile boolean finished;
    volatile String error;
    volatile float gain;
    volatile VideoDecoder.Format format;
    volatile VideoAudio audio;
    volatile long droppedFrames;
    private long displayedPtsUs = -1;
    private volatile long clockStart;
    private long videoEndUs;
    private volatile boolean hasImage;
    final boolean global;
    final long seekUs;
    volatile boolean scheduled;
    private volatile long releaseAt;

    VideoPlayback() { this(false, 0); }

    VideoPlayback(boolean global, long seekUs) {
        this.global = global;
        this.seekUs = seekUs;
    }

    void run(VideoLibraries libraries, Path directory, String file) {
        runSource(libraries, directory, () -> {
            Path root = directory.toRealPath().resolve("frrnq_videos");
            if (!root.equals(root.toRealPath())) { throw new IOException("frrnq_videos must be a local directory."); }
            Path path = root.resolve(file).toRealPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new IOException("Choose an MP4 file inside frrnq_videos.");
            }
            return path;
        });
    }

    void runSource(VideoLibraries libraries, Path directory, Callable<Path> source) {
        try {
            Path path = source.call();
            if (stopped()) { return; }
            libraries.create(directory.resolve("frrnq_video_libs")).decode(path, seekUs, this);
            if (format == null && seekUs > 0) { return; }
            if (!cancelled && !Thread.currentThread().isInterrupted()) {
                if (audio != null && (hasImage || audio.failure != null)) {
                    audio.awaitEnd();
                    if (audio.failure != null) {
                        throw new IOException("Audio output failed: " + audio.failure.getMessage(), audio.failure);
                    }
                }
                if (!hasImage) { throw new IOException("The file contains no decodable video frames."); }
                waitUntil(Math.min(videoEndUs, format.durationUs - seekUs));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError failure) {
            if (!cancelled && !Thread.currentThread().isInterrupted()) {
                LogManager.getLogger("frrnqserverscore/VideoPlayer").warn("Local video playback failed", failure);
                error = failure instanceof IOException || failure instanceof javax.sound.sampled.LineUnavailableException
                        ? failure.getMessage() : "Cannot decode this MP4 or load the Windows x64 FFmpeg runtime. See latest.log.";
                if (error == null) { error = "VideoPlayer could not open the audio output. See latest.log."; }
            }
        } finally {
            try {
                if (audio != null) { audio.close(); }
            } finally {
                finished = true;
            }
        }
    }

    @Override public void begin(VideoDecoder.Format format, VideoDecoder.AudioSource source) {
        this.format = format;
        if (source != null && !stopped()) {
            audio = new VideoAudio(format.sampleRate, format.channels, () -> gain, source, this::startAllowed);
            audio.start();
        }
    }

    boolean ready() {
        return hasImage && (audio == null || audio.prepared);
    }

    void schedule(long at) {
        releaseAt = at;
        scheduled = true;
    }

    private boolean startAllowed() {
        return !global || (scheduled && hasImage && System.nanoTime() - releaseAt >= 0);
    }

    long positionUs() {
        VideoAudio sound = audio;
        if (sound != null) { return sound.positionUs(); }
        if (global) {
            return startAllowed() ? TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - releaseAt) : -1;
        }
        long start = clockStart;
        return start == 0 ? -1 : TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
    }

    private void waitUntil(long timeUs) throws InterruptedException {
        long delay;
        while (!stopped() && (delay = timeUs - positionUs()) > 0) {
            TimeUnit.MICROSECONDS.sleep(Math.min(delay, 10000));
        }
    }

    @Override public void image(long ptsUs, int width, int height, int stride, ByteBuffer bgra) throws Exception {
        if (audio == null && clockStart == 0) { clockStart = System.nanoTime() + 100000000L; }
        // This wait applies solely to the video reader. AAC has its own reader and queue.
        if (hasImage || !global) { waitUntil(ptsUs - VIDEO_LEAD_US); }
        if (stopped()) { return; }
        if (stride < width * 4 || stride % 4 != 0
                || (long) (height - 1) * stride + width * 4 > bgra.remaining()) {
            throw new IOException("Invalid BGRA frame layout.");
        }
        videoEndUs = ptsUs + Math.round(1000000 / format.fps);
        int[] pixels = sparePixels.poll();
        if (pixels == null || pixels.length != width * height) { pixels = new int[width * height]; }
        // BGRA bytes from FFmpeg become the packed ARGB ints expected by TextureUtil.
        IntBuffer data = bgra.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        if (stride == width * 4) {
            data.get(pixels);
        } else {
            for (int y = 0; y < height; y++) {
                data.position(y * (stride / 4));
                data.get(pixels, y * width, width);
            }
        }

        Image image = new Image(ptsUs, width, height, pixels);
        synchronized (images) {
            if (cancelled) { return; }
            if (!images.offer(image)) {
                recycle(images.remove());
                droppedFrames++;
                images.add(image);
            }
            hasImage = true;
        }
    }

    Image frameForDisplay() {
        Image newest = null;
        long now = positionUs();
        if (audio != null && audio.buffering) { return null; }
        // A single consumer removes frames. The producer may discard a full queue's oldest frame.
        // Use the same monitor for selecting a PTS and removing that exact frame.
        synchronized (images) {
            Image next;
            while ((next = images.peek()) != null && next.ptsUs <= now) {
                if (newest != null) { recycle(newest); droppedFrames++; }
                newest = images.poll();
            }
        }
        return newest;
    }

    void recycle(Image image) { sparePixels.offer(image.pixels); }
    void presented(Image image) { displayedPtsUs = image.ptsUs; }

    String avOffset() {
        VideoAudio sound = audio;
        if (sound == null || displayedPtsUs < 0 || sound.playedUs < 0 || sound.ended()) { return "n/a"; }
        return String.format(java.util.Locale.ROOT, "%+d ms", (displayedPtsUs - sound.playedUs) / 1000);
    }

    @Override public boolean stopped() {
        return cancelled || Thread.currentThread().isInterrupted() || (audio != null && audio.failure != null);
    }

    void cancel() {
        cancelled = true;
        synchronized (images) {
            images.clear();
        }
        sparePixels.clear();
    }
}
