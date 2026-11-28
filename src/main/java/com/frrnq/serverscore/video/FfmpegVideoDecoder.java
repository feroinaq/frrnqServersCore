package com.frrnq.serverscore.video;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.SampleMode;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA;

/** Loaded with the isolated JavaCV runtime, with no Minecraft types in its API. */
public final class FfmpegVideoDecoder implements VideoDecoder {
    private static final int MAX_FRAME_SIDE = 1920;
    private static final int MAX_FRAME_PIXELS = 2073600;
    // Bound FFmpeg's native H.264 allocations separately from the output texture budget.
    private static final int MAX_SOURCE_PIXELS = 16777216;
    private static final long MAX_DURATION_US = 120000000L;

    @Override public void decode(Path video, long seekUs, VideoSink sink) throws Exception {
        FFmpegFrameGrabber.tryLoad();
        try (FFmpegFrameGrabber grabber = open(video)) {
            grabber.start();
            if (sink.stopped()) { return; }
            if (!grabber.hasVideo() || grabber.getVideoCodec() != AV_CODEC_ID_H264) {
                throw new IOException("Video must use H.264.");
            }
            int sourceWidth = grabber.getImageWidth();
            int sourceHeight = grabber.getImageHeight();
            long sourcePixels = (long) sourceWidth * sourceHeight;
            if (sourceWidth <= 0 || sourceHeight <= 0 || sourcePixels > MAX_SOURCE_PIXELS) {
                throw new IOException("Source video exceeds the native decoder limit.");
            }
            double sideScale = MAX_FRAME_SIDE / (double) Math.max(sourceWidth, sourceHeight);
            double pixelScale = Math.sqrt(MAX_FRAME_PIXELS / (double) sourcePixels);
            double scale = Math.min(1, Math.min(sideScale, pixelScale));
            int width = Math.max(1, (int) Math.floor(sourceWidth * scale));
            int height = Math.max(1, (int) Math.floor(sourceHeight * scale));
            double sar = grabber.getAspectRatio();
            if (!Double.isFinite(sar) || sar <= 0) { sar = 1; }
            double rotation = grabber.getDisplayRotation();
            if (rotation == 0) {
                String legacy = grabber.getVideoMetadata("rotate");
                if (legacy != null) {
                    try { rotation = Double.parseDouble(legacy); }
                    catch (NumberFormatException ignored) { rotation = 0; }
                }
            }
            if (!Double.isFinite(rotation)) { rotation = 0; }
            grabber.setImageWidth(width);
            grabber.setImageHeight(height);

            boolean hasAudio = grabber.hasAudio();
            int channels = hasAudio ? grabber.getAudioChannels() : 0;
            int rate = hasAudio ? grabber.getSampleRate() : 0;
            if (hasAudio && (grabber.getAudioCodec() != AV_CODEC_ID_AAC || channels < 1 || channels > 2
                    || rate < 8000 || rate > 96000)) {
                throw new IOException("Audio must use mono or stereo AAC at 8-96 kHz.");
            }
            double fps = grabber.getVideoFrameRate();
            long duration = grabber.getLengthInTime();
            if (!(fps > 0 && fps <= 30) || duration <= 0 || duration > MAX_DURATION_US) {
                throw new IOException("Video must have a known duration up to 120 seconds and a frame rate up to 30 FPS.");
            }

            if (seekUs >= duration) { return; }
            if (seekUs > 0) { grabber.setVideoTimestamp(seekUs); }
            long start = grabber.getFormatContext().start_time();
            // If container timing is unknown, use media time zero for both readers.
            final long origin = start == AV_NOPTS_VALUE ? 0 : start;
            sink.begin(new Format(duration, fps, sourceWidth, sourceHeight, width, height, rate, channels, sar, rotation % 360),
                    hasAudio ? output -> decodeAudio(video, origin, seekUs, rate, channels, output) : null);
            while (!sink.stopped()) {
                Frame frame = grabber.grabImage();
                if (frame == null) { break; }
                long pts = timestamp(frame.timestamp, origin) - seekUs;
                sink.image(Math.max(0, pts), frame.imageWidth, frame.imageHeight, frame.imageStride,
                        (ByteBuffer) frame.image[0]);
            }
        }
    }

    private static void decodeAudio(Path video, long origin, long seekUs, int rate, int channels, AudioSink sink) throws Exception {
        try (FFmpegFrameGrabber grabber = open(video)) {
            grabber.setVideoStream(Integer.MAX_VALUE);
            grabber.start();
            if (!grabber.hasAudio() || grabber.getAudioCodec() != AV_CODEC_ID_AAC) {
                throw new IOException("The AAC audio track is unavailable.");
            }
            if (seekUs > 0) { grabber.setAudioTimestamp(seekUs); }
            while (!sink.stopped()) {
                Frame frame = grabber.grabSamples();
                if (frame == null) { break; }
                if (frame.sampleRate != rate || frame.audioChannels != channels || frame.samples.length != 1
                        || !(frame.samples[0] instanceof ShortBuffer)) {
                    throw new IOException("Unsupported PCM layout or audio format change.");
                }
                sink.samples(timestamp(frame.timestamp, origin) - seekUs, (ShortBuffer) frame.samples[0]);
            }
        }
    }

    private static FFmpegFrameGrabber open(Path video) {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video.toFile());
        grabber.setFormat("mov");
        grabber.setOption("protocol_whitelist", "file");
        grabber.setOption("enable_drefs", "0");
        grabber.setOption("use_absolute_path", "0");
        grabber.setVideoOption("threads", "2");
        // H.264 coded dimensions can include macroblock padding beyond the displayed size.
        grabber.setVideoOption("max_pixels", Integer.toString(MAX_SOURCE_PIXELS));
        grabber.setPixelFormat(AV_PIX_FMT_BGRA);
        grabber.setSampleMode(SampleMode.SHORT);
        return grabber;
    }

    private static long timestamp(long timestamp, long origin) throws IOException {
        long pts = timestamp - origin;
        if (pts > MAX_DURATION_US || pts < -1000000L) {
            throw new IOException("Video contains unsupported timestamps.");
        }
        return pts;
    }
}
