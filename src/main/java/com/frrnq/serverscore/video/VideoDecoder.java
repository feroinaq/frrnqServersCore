package com.frrnq.serverscore.video;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/** Decodes independent tracks. Native buffers are valid only for each callback. */
public interface VideoDecoder {
    final class Format {
        final long durationUs;
        final double fps, sampleAspectRatio, rotation;
        final int sourceWidth, sourceHeight;
        final int width, height;
        final int sampleRate, channels;

        public Format(long durationUs, double fps, int sourceWidth, int sourceHeight,
                int width, int height, int sampleRate, int channels, double sampleAspectRatio, double rotation) {
            this.durationUs = durationUs;
            this.fps = fps;
            this.sampleAspectRatio = sampleAspectRatio;
            this.rotation = rotation;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.width = width;
            this.height = height;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    interface VideoSink {
        void begin(Format format, AudioSource audio) throws Exception;
        void image(long ptsUs, int width, int height, int stride, ByteBuffer bgra) throws Exception;
        boolean stopped();
    }

    interface AudioSink {
        void samples(long ptsUs, ShortBuffer pcm) throws Exception;
        boolean stopped();
    }

    interface AudioSource {
        void decode(AudioSink sink) throws Exception;
    }

    void decode(Path video, long seekUs, VideoSink sink) throws Exception;
}
