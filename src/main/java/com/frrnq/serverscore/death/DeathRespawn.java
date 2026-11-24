package com.frrnq.serverscore.death;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DeathRespawn {
    private static final Logger LOG = LogManager.getLogger("frrnqserverscore/DeathLimit");

    /** Conservative check: full supporting block, empty body space, no nearby fire/liquids/cactus. */
    public static boolean isSafe(WorldServer world, BlockPos pos) {
        if (pos.getY() < 1 || pos.getY() > 254 || !world.getWorldBorder().contains(pos)) { return false; }
        world.getChunkFromBlockCoords(pos);
        if (!world.getBlockState(pos.down()).isFullCube()) { return false; }
        AxisAlignedBB body = new AxisAlignedBB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8);
        if (!world.getCollisionBoxes(null, body).isEmpty() || world.containsAnyLiquid(body)) { return false; }
        for (BlockPos nearby : BlockPos.getAllInBox(pos.add(-1, -1, -1), pos.add(1, 2, 1))) {
            Material material = world.getBlockState(nearby).getMaterial();
            if (material.isLiquid() || material == Material.FIRE
                    || world.getBlockState(nearby).getBlock() == Blocks.CACTUS
                    || world.getBlockState(nearby).getBlock() == Blocks.MAGMA) { return false; }
        }
        return true;
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.isEndConquered()) { return; }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        DeathData data = DeathData.get(player.getServer());
        if (data.getLives(player.getUniqueID()) > 0) {
            com.frrnq.serverscore.teamspawn.TeamSpawn.respawn(player);
            return;
        }
        BlockPos pos = data.getSpawn();
        WorldServer destination = player.getServer().getWorld(0);
        if (pos == null || !isSafe(destination, pos)) {
            LOG.warn("DeathLimit: no configured safe eliminated spawn for {} ({}); keeping vanilla respawn.",
                    player.getName(), player.getUniqueID());
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (player.dimension != 0) {
            player.getServer().getPlayerList().transferPlayerToDimension(player, 0,
                    (world, entity, yaw) -> entity.setLocationAndAngles(x, y, z, yaw, 0));
        }
        player.connection.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0;
    }
}