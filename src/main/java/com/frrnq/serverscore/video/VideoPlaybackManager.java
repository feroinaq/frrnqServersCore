package com.frrnq.serverscore.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.frrnq.serverscore.network.VideoControlMessage;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

public final class VideoPlaybackManager {
    private static final float GAME_SOUND_GAIN = .25F;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RemoteVideoClient remote = new RemoteVideoClient(mc, this);
    private final VideoLibraries libraries = new VideoLibraries();
    private final VideoRenderer renderer = new VideoRenderer();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), work -> {
                Thread thread = new Thread(work, "frrnqServersCore VideoPlayer");
                thread.setDaemon(true);
                return thread;
            });
    private VideoPlayback playback;
    private Future<?> worker;
    private World world;
    private boolean ducked;
    private VideoRenderer.Mode mode = VideoRenderer.Mode.CORNER;
    private int opacity = 70;
    private int volume = 80;

    private VideoPlaybackManager() { executor.allowCoreThreadTimeOut(true); }

    public static VideoPlaybackManager init() {
        VideoPlaybackManager player = new VideoPlaybackManager();
        try {
            Files.createDirectories(player.mc.mcDataDir.toPath().resolve("frrnq_videos"));
        } catch (IOException failure) {
            LogManager.getLogger("frrnqserverscore/VideoPlayer").warn("Cannot create frrnq_videos", failure);
        }
        MinecraftForge.EVENT_BUS.register(player);
        ClientCommandHandler.instance.registerCommand(new VideoTestCommand(player));
        MinecraftForge.EVENT_BUS.register(new VideoPreparationHud(player.remote));
        return player;
    }

    public void receive(NetworkManager connection, VideoControlMessage message, long received) {
        remote.receive(connection, message, received);
    }

    boolean remoteActive() { return remote.active(); }

    interface RemoteSource {
        Path open(Path directory) throws IOException, InterruptedException;
    }

    void launchRemote(VideoPlayback session, RemoteSource source) {
        stop();
        session.gain = masterVolume() * remote.volume() / 100.0F;
        playback = session;
        Path directory = mc.mcDataDir.toPath();
        worker = executor.submit(() -> session.runSource(libraries, directory, () -> source.open(directory)));
    }

    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        NetworkManager old = event.getManager();
        mc.addScheduledTask(() -> {
            if (mc.getConnection() == null || mc.getConnection().getNetworkManager() == old) { remote.reset(); }
        });
    }

    void play(String file) {
        stop();
        VideoPlayback session = new VideoPlayback();
        session.gain = masterVolume() * volume / 100.0F;
        playback = session;
        world = mc.world;
        Path directory = mc.mcDataDir.toPath();
        worker = executor.submit(() -> session.run(libraries, directory, file));
    }

    void stop() {
        if (playback == null) { return; }
        playback.cancel();
        worker.cancel(true);
        executor.purge();
        playback = null;
        worker = null;
        world = null;
        renderer.clear();
        if (ducked) {
            mc.getSoundHandler().setSoundLevel(SoundCategory.MASTER, masterVolume());
            ducked = false;
        }
    }

    void setMode(VideoRenderer.Mode mode) { this.mode = mode; }
    void setOpacity(int opacity) { this.opacity = opacity; }
    void setVolume(int volume) { this.volume = volume; }

    String status() {
        if (playback == null) { return "VideoPlayer: stopped."; }
        VideoDecoder.Format format = playback.format;
        if (format == null) { return "VideoPlayer: loading."; }
        VideoAudio sound = playback.audio;
        String state = sound != null && sound.buffering ? "buffering" : "playing";
        String audio = sound == null ? "no audio"
                : String.format(Locale.ROOT, "%d Hz, %d ch, buffer %d/%d ms, underruns %d",
                        format.sampleRate, format.channels, sound.bufferedMs, sound.capacityMs, sound.underruns);
        return String.format(Locale.ROOT,
                "VideoPlayer: %s, %.2f / %.2f s, %s, volume %d%%%n"
                + "Source %dx%d, decoded %dx%d, %.2f FPS, video drops %d%nAudio: %s. Video - audio: %s",
                state, Math.min(format.durationUs, Math.max(0, playback.positionUs())) / 1000000.0,
                format.durationUs / 1000000.0, mode, volume,
                format.sourceWidth, format.sourceHeight, format.width, format.height, format.fps, playback.droppedFrames,
                audio, playback.avOffset());
    }

    private float masterVolume() { return mc.gameSettings.getSoundLevel(SoundCategory.MASTER); }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        World unloaded = event.getWorld();
        if (unloaded.isRemote) {
            mc.addScheduledTask(() -> { if (playback != null && !playback.global && world == unloaded) { stop(); } });
        }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        remote.tick();
        if (playback == null) { return; }
        if (!playback.global && (world != mc.world || mc.player == null || mc.getConnection() == null)) {
            stop();
            return;
        }
        if (playback.finished) {
            if (playback.global) { return; }
            String error = playback.error;
            stop();
            if (error != null) { mc.player.sendMessage(new TextComponentString("VideoPlayer: " + error)); }
            return;
        }
        float master = masterVolume();
        playback.gain = master * (playback.global ? remote.volume() : volume) / 100.0F;
        if (playback.positionUs() >= 0 && master > 0) {
            mc.getSoundHandler().setSoundLevel(SoundCategory.MASTER, master * GAME_SOUND_GAIN);
            ducked = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(RenderGameOverlayEvent.Pre event) {
        if (playback == null || playback.finished || mc.world == null || mc.player == null
                || (!playback.global && mc.world != world)
                || event.getType() != RenderGameOverlayEvent.ElementType.ALL) { return; }
        try {
            VideoRenderer.Mode currentMode = playback.global ? VideoRenderer.Mode.values()[remote.mode()] : mode;
            renderer.render(playback, currentMode, playback.global ? remote.opacity() : opacity,
                    event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
        } catch (RuntimeException | LinkageError failure) {
            LogManager.getLogger("frrnqserverscore/VideoPlayer").warn("Local video rendering failed", failure);
            if (playback.global) {
                remote.renderFailure();
            } else {
                stop();
                if (mc.player != null) { mc.player.sendMessage(new TextComponentString("VideoPlayer: cannot render video. See latest.log.")); }
            }
        }
    }
}
