package com.frrnq.serverscore.teamspawn;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Login confirms a name reservation against the actual connected server profile. */
public final class PendingTeamHandler {
    private static final Logger LOG = LogManager.getLogger("frrnqserverscore/TeamSpawn");
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.world.isRemote) { return; }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        String nickname = player.getGameProfile().getName();
        java.util.UUID uuid = player.getGameProfile().getId();
        if (nickname == null || uuid == null) { return; }
        TeamData data = TeamData.get(player.getServer());
        String requested = data.pendingTeam(nickname);
        if (requested == null) { return; }
        String current = data.team(uuid);
        if (current != null && !current.equals(requested)) {
            LOG.warn("TeamSpawn: \u043a\u043e\u043d\u0444\u043b\u0438\u043a\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f {} ({}): \u0443\u0436\u0435 \u0432 {}, \u043e\u0436\u0438\u0434\u0430\u0435\u0442 {}. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0440\u0435\u0448\u0435\u043d\u0438\u0435 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u0430.", nickname, uuid, current, requested);
            return;
        }
        data.add(uuid, requested);
        data.removePending(nickname);
        data.save(player.getServer());
        LOG.info("TeamSpawn: \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u043e \u0434\u043b\u044f {} ({}) \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 {}.", nickname, uuid, requested);
    }
}