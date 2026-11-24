package com.frrnq.serverscore.teamspawn;

import com.frrnq.serverscore.death.DeathRespawn;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;

public final class TeamSpawn {
    private TeamSpawn() {}
    public static boolean hasValidBed(EntityPlayerMP player) {
        // Check the actual bed and its exit, not merely saved (possibly stale) coordinates.
        WorldServer world = player.getServerWorld();
        BlockPos bed = player.getBedLocation(player.dimension);
        if (bed == null) { return false; }
        world.getChunkFromBlockCoords(bed);
        net.minecraft.block.state.IBlockState state = world.getBlockState(bed);
        return state.getBlock().isBed(state, world, bed, player)
                && EntityPlayer.getBedSpawnLocation(world, bed, false) != null;
    }
    public static BlockPos findPosition(EntityPlayerMP player, BlockPos center, Set<BlockPos> reserved) {
        WorldServer world = player.getServer().getWorld(0);
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) { continue; }
                    for (int dy : new int[] {0, 1, -1, 2, -2}) {
                        BlockPos candidate = center.add(dx, dy, dz);
                        if (reserved.contains(candidate) || !DeathRespawn.isSafe(world, candidate)) { continue; }
                        AxisAlignedBB body = new AxisAlignedBB(candidate.getX() + 0.2, candidate.getY(), candidate.getZ() + 0.2,
                                candidate.getX() + 0.8, candidate.getY() + 1.8, candidate.getZ() + 0.8);
                        boolean occupied = false;
                        for (EntityPlayer other : world.getEntitiesWithinAABB(EntityPlayer.class, body)) {
                            if (other != player && !other.isDead) { occupied = true; break; }
                        }
                        for (BlockPos used : reserved) {
                            if (used.getX() == candidate.getX() && used.getZ() == candidate.getZ()
                                    && Math.abs(used.getY() - candidate.getY()) < 2) { occupied = true; break; }
                        }
                        if (!occupied) { return candidate; }
                    }
                }
            }
        }
        return null;
    }
    public static void teleport(EntityPlayerMP player, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        if (player.isRiding()) { player.dismountRidingEntity(); }
        if (player.dimension != 0) {
            player.getServer().getPlayerList().transferPlayerToDimension(player, 0,
                    (world, entity, yaw) -> entity.setLocationAndAngles(x, y, z, yaw, 0));
        }
        player.connection.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0;
    }
    /** Invoked only by the existing DeathLimit respawn handler, after the lives check. */
    public static void respawn(EntityPlayerMP player) {
        if (hasValidBed(player)) { return; }
        BlockPos center = TeamData.get(player.getServer()).spawn(player.getUniqueID());
        if (center == null) { return; }
        BlockPos safe = findPosition(player, center, new HashSet<>());
        if (safe == null) {
            LogManager.getLogger("frrnqserverscore/TeamSpawn").warn("No safe team spawn for {}; keeping vanilla respawn.", player.getName());
            return;
        }
        teleport(player, safe);
    }
}