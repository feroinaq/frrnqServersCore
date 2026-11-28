package com.frrnq.serverscore.video;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** AAC decoding supplies copied PCM to a dedicated Java Sound output thread. */
final class VideoAudio implements VideoDecoder.AudioSink, AutoCloseable {
    private static final int PREBUFFER_MS = 400;
    private static final int LINE_BUFFER_MS = 500;
    private static final int QUEUE_BLOCKS = 32;
    private static final int BLOCK_FRAMES = 512;

    private static final class Pcm {
        final long firstFrame;
        final short[] samples;

        Pcm(long firstFrame, short[] samples) {
            this.firstFrame = firstFrame;
            this.samples = samples;
        }
    }

    private final ArrayBlockingQueue<Pcm> pending = new ArrayBlockingQueue<>(QUEUE_BLOCKS);
    private final int rate;
    private final int channels;
    private final DoubleSupplier gain;
    private final BooleanSupplier startAllowed;
    private final Thread decoder;
    private final Thread output;
    private volatile boolean cancelled;
    private volatile boolean inputEnded;
    private volatile long endNanos;
    volatile Throwable failure;
    volatile long playedUs = -1;
    volatile boolean buffering = true;
    volatile boolean prepared;
    volatile int bufferedMs;
    volatile int capacityMs;
    volatile int underruns;

    VideoAudio(int rate, int channels, DoubleSupplier gain, VideoDecoder.AudioSource source, BooleanSupplier startAllowed) {
        this.rate = rate;
        this.channels = channels;
        this.gain = gain;
        this.startAllowed = startAllowed;
        decoder = worker("VideoPlayer AAC", () -> decode(source));
        output = worker("VideoPlayer PCM", this::play);
    }

    private static Thread worker(String name, Runnable work) {
        Thread thread = new Thread(work, "frrnqServersCore " + name);
        thread.setDaemon(true);
        return thread;
    }

    void start() {
        output.start();
        decoder.start();
    }

    private void decode(VideoDecoder.AudioSource source) {
        try {
            source.decode(this);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError problem) {
            if (!stopped()) { failure = problem; }
        } finally {
            inputEnded = true;
        }
    }

    @Override public void samples(long ptsUs, ShortBuffer pcm) throws IOException, InterruptedException {
        if (stopped()) { return; }
        int count = pcm.remaining();
        if (count == 0) { return; }
        if (count % channels != 0 || count / channels > 8192) {
            throw new IOException("Invalid interleaved PCM block size.");
        }
        short[] copy = new short[count];
        pcm.duplicate().get(copy);
        Pcm block = new Pcm(Math.round(ptsUs * rate / 1000000.0), copy);
        while (!stopped() && !pending.offer(block, 20, TimeUnit.MILLISECONDS)) {
            // Only the AAC reader waits for queue space. The video reader is independent.
        }
    }

    private void play() {
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            int frameBytes = format.getFrameSize();
            line.open(format, rate * LINE_BUFFER_MS / 1000 * frameBytes);
            pump(line, frameBytes);
            if (!stopped()) {
                if (playedUs < 0) { playedUs = 0; }
                endNanos = System.nanoTime();
                buffering = false;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException unsupported) {
            if (!stopped()) { failure = new IOException("No Java Sound output supports this PCM format.", unsupported); }
        } catch (LineUnavailableException | IOException | RuntimeException problem) {
            if (!stopped()) { failure = problem; }
        }
    }

    private void pump(SourceDataLine line, int frameBytes) throws InterruptedException, IOException {
        int capacity = line.getBufferSize() / frameBytes;
        if (capacity <= 0) { throw new IOException("Java Sound opened an empty output buffer."); }
        int prebuffer = Math.min(capacity, Math.max(1, rate * PREBUFFER_MS / 1000));
        capacityMs = (int) (capacity * 1000L / rate);
        byte[] bytes = new byte[BLOCK_FRAMES * frameBytes];
        long written = 0;
        long played = 0;
        long progressAt = System.nanoTime();
        boolean running = false;
        Pcm block = null;
        int offset = 0;
        long blockStart = 0;
        while (!stopped()) {
            long actual = Math.min(written, Math.max(played, line.getLongFramePosition()));
            long queued = written - actual;
            if (actual != played || !running || queued == 0) { progressAt = System.nanoTime(); }
            if (System.nanoTime() - progressAt > TimeUnit.SECONDS.toNanos(5)) {
                throw new IOException("Java Sound output stopped advancing.");
            }
            played = actual;
            if (running) { playedUs = toMicros(played); }
            bufferedMs = (int) (queued * 1000 / rate);

            if (block == null) {
                block = pending.poll();
                if (block != null) {
                    // Samples before time zero are AAC preroll. Keep positive-time PCM sequential.
                    offset = (int) Math.min(block.samples.length / channels, Math.max(0, -block.firstFrame));
                    blockStart = Math.max(0, block.firstFrame);
                    if (blockStart - written <= rate / 500) { blockStart = written; }
                    if (offset * channels == block.samples.length) { block = null; continue; }
                }
            }

            boolean drainedInput = inputEnded && block == null && pending.isEmpty();
            if (drainedInput && queued == 0) {
                prepared = true;
                if (startAllowed.getAsBoolean()) { return; }
            }
            if (running && queued == 0) {
                line.stop();
                running = false;
                buffering = true;
                underruns++;
            }
            if (!running && (queued >= prebuffer || drainedInput)) { prepared = true; }
            if (!running && prepared && startAllowed.getAsBoolean() && (queued >= prebuffer || drainedInput)) {
                line.start();
                running = true;
                playedUs = toMicros(played);
                buffering = false;
            }

            int free = Math.min(BLOCK_FRAMES, line.available() / frameBytes);
            if (block == null || free == 0) {
                TimeUnit.MILLISECONDS.sleep(3);
                continue;
            }
            int frames;
            boolean gap = written < blockStart;
            if (gap) {
                // Preserve actual gaps in the track's timestamps.
                frames = (int) Math.min(free, blockStart - written);
                Arrays.fill(bytes, 0, frames * frameBytes, (byte) 0);
            } else {
                frames = Math.min(free, block.samples.length / channels - offset);
                double volume = gain.getAsDouble();
                for (int i = 0; i < frames * channels; i++) {
                    int sample = (int) Math.round(block.samples[offset * channels + i] * volume);
                    bytes[2 * i] = (byte) sample;
                    bytes[2 * i + 1] = (byte) (sample >> 8);
                }
            }
            int supplied = line.write(bytes, 0, frames * frameBytes) / frameBytes;
            written += supplied;
            if (!gap) {
                offset += supplied;
                if (offset * channels == block.samples.length) { block = null; }
            }
            if (supplied == 0) { TimeUnit.MILLISECONDS.sleep(3); }
        }
    }

    private long toMicros(long frames) { return frames * 1000000L / rate; }

    long positionUs() {
        long end = endNanos;
        return end == 0 ? playedUs : playedUs + TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - end);
    }

    boolean ended() { return endNanos != 0; }
    @Override public boolean stopped() { return cancelled || failure != null || Thread.currentThread().isInterrupted(); }

    void awaitEnd() throws InterruptedException {
        output.join();
        decoder.join();
    }

    @Override public void close() {
        cancelled = true;
        output.interrupt();
        decoder.interrupt();
        boolean interrupted = false;
        while (output.isAlive() || decoder.isAlive()) {
            try {
                awaitEnd();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        pending.clear();
        if (interrupted) { Thread.currentThread().interrupt(); }
    }
}
