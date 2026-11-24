package com.frrnq.serverscore.death;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public final class DeathHandler {
    private static final String COUNTED = "frrnqserverscore.deathCounted";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || event.getEntityLiving().world.isRemote) { return; }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        // Entity-local marker is not copied to the new respawn entity.
        if (player.getEntityData().getBoolean(COUNTED)) { return; }
        player.getEntityData().setBoolean(COUNTED, true);
        DeathData data = DeathData.get(player.getServer());
        data.setLives(player.getUniqueID(), Math.max(0, data.getLives(player.getUniqueID()) - 1));
        data.save(player.getServer());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) { return; }
        DeathData data = DeathData.get(event.player.getServer());
        data.getLives(event.player.getUniqueID());
        data.save(event.player.getServer());
    }
}