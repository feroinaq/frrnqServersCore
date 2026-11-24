package com.frrnq.serverscore.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.frrnq.serverscore.death.DeathData;
import com.frrnq.serverscore.teamspawn.TeamData;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Mutations invalidate the snapshot; one END-tick update coalesces confirmation and death events. */
public final class TeamStatusSync {
    private static boolean dirty = true;
    private static TeamStatusMessage last;
    private static final Set<UUID> pending = new HashSet<>();
    public static void changed() { dirty = true; }
    public static void clear() { dirty = true; last = null; pending.clear(); }

    private static TeamStatusMessage snapshot(MinecraftServer server) {
        TeamData teams = TeamData.get(server);
        if (!teams.hudActive()) { return new TeamStatusMessage(false, java.util.Collections.emptyList()); }
        DeathData deaths = DeathData.get(server);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : teams.teams().keySet()) { counts.put(name, 0); }
        Set<String> confirmedNames = new HashSet<>();
        for (Map.Entry<UUID, String> member : teams.members().entrySet()) {
            if (deaths.peekLives(member.getKey()) > 0) { counts.computeIfPresent(member.getValue(), (key, count) -> count + 1); }
            EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(member.getKey());
            GameProfile profile = online != null ? online.getGameProfile() : server.getPlayerProfileCache().getProfileByUUID(member.getKey());
            if (profile != null && profile.getName() != null) { confirmedNames.add(TeamData.normalize(profile.getName())); }
        }
        for (Map.Entry<String, String> waiting : teams.pending().entrySet()) {
            // Pending conflicts remain in TeamSpawn for the administrator, but never count a known member twice.
            if (!confirmedNames.contains(waiting.getKey())) { counts.computeIfPresent(waiting.getValue(), (key, count) -> count + 1); }
        }
        List<TeamStatusMessage.Row> rows = new ArrayList<>();
        counts.forEach((name, count) -> {
            if (count > 0) { rows.add(new TeamStatusMessage.Row(name, teams.color(name), count)); }
        });
        return new TeamStatusMessage(true, rows);
    }

    private static void queue(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) { pending.add(player.getUniqueID()); dirty = true; }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { queue(event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { queue(event.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { queue(event.player); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { pending.remove(event.player.getUniqueID()); }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (!dirty && pending.isEmpty())) { return; }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getWorld(0) == null) { return; }
        TeamStatusMessage message = snapshot(server);
        boolean broadcast = !message.sameAs(last);
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (broadcast || pending.contains(player.getUniqueID())) { CoreNetwork.CHANNEL.sendTo(message, player); }
        }
        last = message;
        dirty = false;
        pending.clear();
    }
}
