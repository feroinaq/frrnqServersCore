package com.frrnq.serverscore.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.frrnq.serverscore.death.DeathData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-thread only; unchanged values never generate packets. */
public final class DeathSync {
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static final Set<UUID> PENDING = new HashSet<>();

    public static void changed(MinecraftServer server) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) { send(player, false); }
    }
    private static void send(EntityPlayerMP player, boolean force) {
        DeathData data = DeathData.get(player.getServer());
        int lives = data.getLives(player.getUniqueID());
        int maximum = data.getLimit();
        int key = maximum * 101 + lives;
        if (force || !Integer.valueOf(key).equals(SENT.get(player.getUniqueID()))) {
            CoreNetwork.CHANNEL.sendTo(new LivesMessage(lives, maximum), player);
            SENT.put(player.getUniqueID(), key);
        }
    }
    public static void clear() { SENT.clear(); PENDING.clear(); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { queue(event.player); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { queue(event.player); }
    private void queue(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) { PENDING.add(player.getUniqueID()); }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            SENT.remove(event.player.getUniqueID());
            PENDING.remove(event.player.getUniqueID());
        }
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) { return; }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        for (UUID uuid : PENDING) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
            if (player != null) { send(player, true); }
        }
        PENDING.clear();
    }
}