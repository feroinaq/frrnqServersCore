package com.frrnq.serverscore.video;

import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.VideoControlMessage;
import com.frrnq.serverscore.network.VideoFeedbackMessage;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;

final class RemoteVideoClient {
    private static final long START_TOLERANCE_US = 500000;
    private static final long CATCH_UP_LEAD_US = 900000;
    private static final long DRIFT_LIMIT_US = 750000;
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final Minecraft mc;
    private final VideoPlaybackManager manager;
    private final VideoCache cache = new VideoCache();
    private NetworkManager connection;
    private long lastSession;
    private VideoControlMessage state;
    private VideoCache.Transfer transfer;
    private URI source;
    private VideoPlayback job;
    private boolean terminal;
    private boolean sentDownload, sentReady;
    private long nextPing, probe, offset, bestRtt = Long.MAX_VALUE, sampledAt;
    private long retryAt;
    private int seeks;
    private VideoControlMessage preparation;

    RemoteVideoClient(Minecraft mc, VideoPlaybackManager manager) {
        this.mc = mc;
        this.manager = manager;
    }

    boolean active() { return state != null; }
    int mode() { return state.mode; }
    int volume() { return state.volume; }
    int opacity() { return state.opacity; }

    void receive(NetworkManager from, VideoControlMessage message, long received) {
        if (connection != from) {
            reset();
            connection = from;
        }
        if (message.kind == VideoControlMessage.Kind.STOP) {
            if (message.session >= lastSession) {
                clearSession();
                lastSession = message.session;
            }
            return;
        }
        if (message.kind == VideoControlMessage.Kind.PREPARATION) {
            if (state != null && state.session == message.session) { preparation = message; }
            return;
        }
        if (message.kind == VideoControlMessage.Kind.CLOCK) {
            if (state != null && state.session == message.session && message.echo == probe) {
                long rtt = received - probe;
                if (rtt >= 0 && rtt < TimeUnit.SECONDS.toNanos(2)
                        && (rtt < bestRtt || received - sampledAt > TimeUnit.SECONDS.toNanos(12))) {
                    bestRtt = rtt;
                    sampledAt = received;
                    offset = message.serverNanos - (probe + rtt / 2);
                }
            }
            return;
        }
        if (message.session == lastSession && state != null) {
            state = message;
            return;
        }
        if (message.session <= lastSession || message.url.isEmpty()) { return; }
        clearSession();
        manager.stop();
        lastSession = message.session;
        state = message;
        try {
            source = VideoDomains.check(message.url);
        } catch (IllegalArgumentException rejected) {
            fail("Video source is outside the client allowlist.", 1);
            return;
        }
        transfer = new VideoCache.Transfer();
        launch(0);
    }

    private void launch(long seekUs) {
        URI url = source;
        VideoCache.Transfer progress = transfer;
        job = new VideoPlayback(true, seekUs);
        VideoPlayback session = job;
        manager.launchRemote(session, directory -> {
            if (progress.file == null) {
                try {
                    progress.file = cache.fetch(directory, url, progress, session::stopped);
                } catch (VideoCache.Failure controlled) {
                    throw controlled;
                } catch (IOException | RuntimeException failure) {
                    // URLConnection exceptions can include signed URLs. Keep only a safe error.
                    throw new IOException("Video download failed. Check HTTPS, connectivity and cache directory.");
                }
            }
            return progress.file;
        });
    }

    void tick() {
        if (connection == null) { return; }
        if (mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection) {
            reset();
            return;
        }
        if (state == null || terminal) { return; }
        long now = System.nanoTime();
        if (nextPing == 0 || now - nextPing >= 0) {
            probe = now;
            nextPing = now + TimeUnit.MILLISECONDS.toNanos(bestRtt == Long.MAX_VALUE ? 500 : 2000);
            send(VideoFeedbackMessage.Kind.CLOCK, probe);
        }
        long position = bestRtt == Long.MAX_VALUE ? Long.MIN_VALUE : (now + offset - state.startNanos) / 1000;
        if (state.durationUs > 0 && position >= state.durationUs) {
            finish();
            return;
        }
        if (job == null) { return; }
        if (job.finished) {
            if (job.error != null) { fail(job.error, transfer.file == null ? 2 : 0); }
            else { finish(); }
            return;
        }
        if (!sentDownload && transfer.file != null) {
            sentDownload = true;
            send(VideoFeedbackMessage.Kind.DOWNLOADED, 0);
        }
        if (!job.ready() || bestRtt == Long.MAX_VALUE) { return; }
        if (!sentReady) {
            sentReady = true;
            send(VideoFeedbackMessage.Kind.READY, job.format.durationUs);
        }
        if (state.durationUs <= 0) { return; }

        long end = Math.min(state.durationUs, job.format.durationUs);
        if (position >= end) { finish(); return; }
        if (!job.scheduled) {
            if (position - job.seekUs > START_TOLERANCE_US && tryCatchUp(now, position, end)) { return; }
            job.schedule(state.startNanos - offset + job.seekUs * 1000);
        } else if (job.positionUs() >= 0 && position - (job.positionUs() + job.seekUs) > DRIFT_LIMIT_US) {
            tryCatchUp(now, position, end);
        }
    }

    private boolean tryCatchUp(long now, long position, long end) {
        if (seeks >= 4 || (seeks > 0 && now - retryAt < 0) || end - position <= CATCH_UP_LEAD_US + 100000) {
            return false;
        }
        seeks++;
        retryAt = now + RETRY_INTERVAL;
        launch(Math.max(0, position) + CATCH_UP_LEAD_US);
        return true;
    }

    private void send(VideoFeedbackMessage.Kind kind, long data) {
        CoreNetwork.CHANNEL.sendToServer(new VideoFeedbackMessage(kind, state.session, data));
    }

    void renderFailure() { fail("Video cannot be rendered.", 3); }

    private void fail(String detail, int reason) {
        if (job == null || job.error == null) {
            org.apache.logging.log4j.LogManager.getLogger("frrnqserverscore/VideoPlayer").warn(detail);
        }
        send(VideoFeedbackMessage.Kind.ERROR, reason);
        finish();
    }

    private void finish() {
        manager.stop();
        job = null;
        transfer = null;
        terminal = true;
    }

    private void clearSession() {
        if (state != null) { finish(); }
        state = null;
        preparation = null;
        source = null;
        terminal = false;
        sentDownload = false;
        sentReady = false;
        seeks = 0;
        nextPing = 0;
        bestRtt = Long.MAX_VALUE;
    }

    void reset() {
        clearSession();
        lastSession = 0;
        connection = null;
    }

    VideoControlMessage preparation() {
        if (preparation == null || preparation.phase == VideoControlMessage.Preparation.HIDDEN) { return null; }
        if (job != null && job.positionUs() >= 0) { return null; }
        if (state.durationUs > 0 && bestRtt != Long.MAX_VALUE
                && System.nanoTime() + offset - state.startNanos >= 0) { return null; }
        return preparation;
    }
}
