package com.frrnq.serverscore.video;

import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.VideoControlMessage;
import com.frrnq.serverscore.network.VideoFeedbackMessage;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.frrnq.serverscore.network.VideoControlMessage.Preparation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-thread state. The network thread only checks the session ID and a per-connection rate limit. */
@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID)
public final class VideoSessions {
    private static final long WAIT = TimeUnit.SECONDS.toNanos(30);
    private static final long START_DELAY = TimeUnit.SECONDS.toNanos(2);
    private static final Map<NetHandlerPlayServer, Rate> rates = new ConcurrentHashMap<>();
    private static volatile long activeId;
    private static long nextId;
    private static Session current;
    private static int mode = 1, opacity = 70, volume = 80;
    private static String idleState = "IDLE";

    private static final class Session {
        final long id = ++nextId;
        final URI source;
        final Map<UUID, Viewer> viewers = new HashMap<>();
        final Set<UUID> preparationRecipients = new HashSet<>();
        VideoControlMessage lastPreparation;
        long nextPreparationCheck;
        ICommandSender issuer;
        boolean requested;
        long deadline;
        long startNanos;
        int durationUs;

        Session(URI source) { this.source = source; }
    }

    private static final class Viewer {
        boolean downloaded;
        boolean failed;
        int durationUs;
    }

    private static final class Rate {
        private long window = System.nanoTime();
        private int used;

        synchronized boolean allow() {
            long now = System.nanoTime();
            if (now - window >= TimeUnit.SECONDS.toNanos(1)) { used = 0; window = now; }
            return used < 8 && ++used <= 8;
        }
    }

    private VideoSessions() {}

    static void prepare(URI source, ICommandSender sender, boolean play) {
        current = new Session(source);
        activeId = current.id;
        if (play) { requestStart(sender); }
        CoreNetwork.CHANNEL.sendToAll(state(true));
    }

    static boolean requestStart(ICommandSender issuer) {
        if (current == null || current.requested || current.durationUs > 0) { return false; }
        current.issuer = issuer;
        current.requested = true;
        current.deadline = System.nanoTime() + WAIT;
        return true;
    }

    static boolean stop() {
        if (current == null) { idleState = "IDLE"; return false; }
        end("IDLE");
        return true;
    }

    private static void end(String state) {
        CoreNetwork.CHANNEL.sendToAll(new VideoControlMessage(VideoControlMessage.Kind.STOP, current.id));
        current = null;
        activeId = 0;
        idleState = state;
    }

    public static void clear() {
        current = null;
        activeId = 0;
        rates.clear();
        idleState = "IDLE";
        mode = 1; opacity = 70; volume = 80;
    }

    static void setMode(int value) { mode = value; sync(); }
    static void setOpacity(int value) { opacity = value; sync(); }
    static void setVolume(int value) { volume = value; sync(); }

    private static void sync() {
        if (current != null) { CoreNetwork.CHANNEL.sendToAll(state(false)); }
    }

    private static VideoControlMessage state(boolean includeSource) {
        VideoControlMessage message = new VideoControlMessage(VideoControlMessage.Kind.STATE, current.id);
        message.url = includeSource ? current.source.toASCIIString() : "";
        message.startNanos = current.startNanos;
        message.durationUs = current.durationUs;
        message.mode = mode;
        message.opacity = opacity;
        message.volume = volume;
        return message;
    }

    static String status(MinecraftServer server) {
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        String phase = idleState;
        int downloaded = 0, ready = 0, errors = 0;
        double position = 0;
        if (current != null) {
            for (EntityPlayerMP player : players) {
                Viewer viewer = current.viewers.get(player.getUniqueID());
                if (viewer == null) { continue; }
                if (viewer.downloaded) { downloaded++; }
                if (viewer.failed) { errors++; }
                if (viewer.durationUs > 0 && !viewer.failed) { ready++; }
            }
            if (current.durationUs > 0) {
                long age = System.nanoTime() - current.startNanos;
                phase = age >= 0 ? "PLAYING" : "READY (scheduled)";
                position = Math.min(Math.max(0, age) / 1000000000.0, current.durationUs / 1000000.0);
            } else if (ready > 0) { phase = "READY"; }
            else if (downloaded > 0) { phase = "BUFFERING"; }
            else { phase = "DOWNLOADING"; }
        }
        return String.format(Locale.ROOT,
                "Video %s | source %s | online %d | downloaded %d | ready %d | errors %d | %.2f s | %s | volume %d%% | opacity %d%%",
                phase, current == null ? "-" : current.source.getHost(), players.size(), downloaded, ready, errors, position,
                VideoCommand.MODES.get(mode), volume, opacity);
    }

    public static void receive(NetHandlerPlayServer connection, VideoFeedbackMessage message) {
        if (message.session != activeId || !rates.computeIfAbsent(connection, key -> new Rate()).allow()) { return; }
        EntityPlayerMP player = connection.player;
        player.getServerWorld().addScheduledTask(() -> {
            if (current == null || current.id != message.session
                    || player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID()) != player) { return; }
            if (message.kind == VideoFeedbackMessage.Kind.CLOCK) {
                VideoControlMessage answer = new VideoControlMessage(VideoControlMessage.Kind.CLOCK, current.id);
                answer.echo = message.data;
                answer.serverNanos = System.nanoTime();
                CoreNetwork.CHANNEL.sendTo(answer, player);
                return;
            }
            Viewer viewer = current.viewers.computeIfAbsent(player.getUniqueID(), id -> new Viewer());
            switch (message.kind) {
                case DOWNLOADED: viewer.downloaded = true; break;
                case READY:
                    if (!viewer.failed && viewer.durationUs == 0) {
                        viewer.downloaded = true;
                        viewer.durationUs = (int) message.data;
                    }
                    break;
                case ERROR:
                    if (!viewer.failed) {
                        viewer.failed = true;
                        String problem = message.data == 1 ? "source policy"
                                : message.data == 2 ? "download" : message.data == 3 ? "rendering" : "playback";
                        org.apache.logging.log4j.LogManager.getLogger("frrnqserverscore/VideoPlayer")
                                .warn("Video session {}: {} reported a {} error", current.id, player.getName(), problem);
                    }
                    break;
                default: break;
            }
        });
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (current != null && event.player instanceof EntityPlayerMP) {
            CoreNetwork.CHANNEL.sendTo(state(true), (EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) { rates.remove(((EntityPlayerMP) event.player).connection); }
        if (current != null) {
            current.viewers.remove(event.player.getUniqueID());
            current.preparationRecipients.remove(event.player.getUniqueID());
        }
    }

    private static void syncPreparation(List<EntityPlayerMP> players, long now) {
        if (current.lastPreparation != null && now - current.nextPreparationCheck < 0) { return; }
        current.nextPreparationCheck = now + TimeUnit.MILLISECONDS.toNanos(500);
        VideoControlMessage summary = new VideoControlMessage(VideoControlMessage.Kind.PREPARATION, current.id);
        if (current.durationUs == 0 || now - current.startNanos < 0) {
            summary.total = players.size();
            boolean buffering = false;
            for (EntityPlayerMP player : players) {
                Viewer viewer = current.viewers.get(player.getUniqueID());
                if (viewer == null) { continue; }
                if (viewer.failed) { summary.errors++; }
                else if (viewer.durationUs > 0) { summary.ready++; }
                else if (viewer.downloaded) { buffering = true; }
            }
            if (current.durationUs > 0) { summary.phase = Preparation.SCHEDULED; }
            else if (summary.total > 0 && summary.errors == summary.total) { summary.phase = Preparation.ERROR; }
            else if (summary.total > 0 && summary.ready + summary.errors == summary.total) { summary.phase = Preparation.READY; }
            else { summary.phase = buffering ? Preparation.BUFFERING : Preparation.DOWNLOADING; }
        }
        VideoControlMessage previous = current.lastPreparation;
        boolean changed = previous == null || previous.phase != summary.phase || previous.total != summary.total
                || previous.ready != summary.ready || previous.errors != summary.errors;
        for (EntityPlayerMP player : players) {
            UUID id = player.getUniqueID();
            if (summary.phase != Preparation.HIDDEN && player.canUseCommand(2, "fcore")) {
                boolean newRecipient = current.preparationRecipients.add(id);
                if (changed || newRecipient) { CoreNetwork.CHANNEL.sendTo(summary, player); }
            } else if (current.preparationRecipients.remove(id)) {
                CoreNetwork.CHANNEL.sendTo(new VideoControlMessage(VideoControlMessage.Kind.PREPARATION, current.id), player);
            }
        }
        current.lastPreparation = summary;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) { return; }
        long now = System.nanoTime();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        syncPreparation(players, now);
        if (current.durationUs > 0) {
            if (now - current.startNanos >= current.durationUs * 1000L) { end("FINISHED"); }
            return;
        }
        if (!current.requested) { return; }
        int waiting = 0;
        int duration = 0;
        for (EntityPlayerMP player : players) {
            Viewer viewer = current.viewers.get(player.getUniqueID());
            if (viewer == null || (!viewer.failed && viewer.durationUs == 0)) { waiting++; }
            else if (!viewer.failed) { duration = Math.max(duration, viewer.durationUs); }
        }
        if (waiting > 0 && now - current.deadline < 0) { return; }
        if (duration == 0) {
            current.issuer.sendMessage(new TextComponentString("Video: no client was ready. Check the client domain allowlist and video runtime."));
            end("ERROR");
            return;
        }
        current.durationUs = duration;
        current.startNanos = now + START_DELAY;
        current.issuer = null;
        sync();
    }
}
