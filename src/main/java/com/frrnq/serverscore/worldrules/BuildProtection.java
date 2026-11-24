package com.frrnq.serverscore.worldrules;

import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class BuildProtection {
    private final WorldRulesConfig settings;

    public BuildProtection(WorldRulesConfig settings) {
        this.settings = settings;
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isRemote || !settings.isBuildProtectionEnabled()
                || event.getWorld().provider.getDimension() != settings.getDimensionId()
                || !(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        // One subscriber handles the base event and both multi-place hierarchies.
        List<BlockSnapshot> snapshots = null;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            snapshots = ((BlockEvent.EntityMultiPlaceEvent) event).getReplacedBlockSnapshots();
        } else if (event instanceof BlockEvent.MultiPlaceEvent) {
            snapshots = ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots();
        }
        boolean denied = false;
        if (snapshots != null) {
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot.getPos().getY() < settings.getMinBuildY()
                        && snapshot.getCurrentBlock().getBlock() != Blocks.LADDER) {
                    denied = true;
                    break;
                }
            }
        } else {
            denied = event.getPos().getY() < settings.getMinBuildY()
                    && event.getPlacedBlock().getBlock() != Blocks.LADDER;
        }
        if (denied) {
            event.setCanceled(true);
            ((EntityPlayer) event.getEntity()).sendStatusMessage(new TextComponentString(
                    "\u041d\u0438\u0436\u0435 Y=" + settings.getMinBuildY() + " \u043c\u043e\u0436\u043d\u043e \u0443\u0441\u0442\u0430\u043d\u0430\u0432\u043b\u0438\u0432\u0430\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e minecraft:ladder."), true);
        }
    }
}