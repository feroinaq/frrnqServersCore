package com.frrnq.serverscore.resourcechests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.ResourceNoticeMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** All operations run on the server thread; no operation requests an unloaded chunk. */
@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID)
public final class ResourceLocations {
    private ResourceLocations() {}
    private static boolean serverOverworld(World world) { return world != null && !world.isRemote && world.provider.getDimension() == 0; }
    private static ResourceLocationData.Location binding(ResourceChestTile tile) {
        World world = tile.getWorld();
        ResourceLocationData data = ResourceLocationData.get(world);
        ResourceLocationData.Location location = data.at(tile.getPos());
        if (location == null) { tile.clearLocation(); return null; }
        UUID token = location.chests().get(tile.getPos());
        if (!tile.matchesLocation(location.id, token)) {
            // A new/replaced/copied block must never inherit an old position's assignment.
            data.unbind(tile.getPos()); tile.clearLocation(); return null;
        }
        return location;
    }
    public static void applyPending(ResourceChestTile tile) {
        World world = tile.getWorld();
        if (!serverOverworld(world) || !world.isBlockLoaded(tile.getPos())) { return; }
        ResourceLocationData.Location location = binding(tile);
        if (location != null && tile.appliedGeneration() < location.generation()) {
            // Commit the revision before refill: its container synchronization reads this inventory.
            tile.setAppliedGeneration(location.generation());
            tile.refill();
        }
    }
    public static void acknowledgeRefill(ResourceChestTile tile) {
        if (!serverOverworld(tile.getWorld())) { return; }
        ResourceLocationData.Location location = binding(tile);
        if (location != null) { tile.setAppliedGeneration(location.generation()); }
    }
    public static boolean unbind(World world, BlockPos pos) {
        if (!serverOverworld(world)) { return false; }
        ResourceLocationData data = ResourceLocationData.get(world);
        boolean removed = data.unbind(pos);
        if (world.isBlockLoaded(pos)) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ResourceChestTile) { ((ResourceChestTile) tile).clearLocation(); }
        }
        if (removed) { reconcile((WorldServer) world, data); }
        return removed;
    }
    public static void pruneLoaded(WorldServer world, ResourceLocationData data, ResourceLocationData.Location location) {
        for (Map.Entry<BlockPos, UUID> entry : new ArrayList<>(location.chests().entrySet())) {
            BlockPos pos = entry.getKey();
            if (!world.isBlockLoaded(pos)) { continue; }
            if (world.getBlockState(pos).getBlock() != ResourceChests.BLOCK) { data.unbind(pos); continue; }
            TileEntity tile = world.getTileEntity(pos);
            // A tile may still be queued for loading; do not discard a legitimate binding then.
            if (tile instanceof ResourceChestTile && !((ResourceChestTile) tile).matchesLocation(location.id, entry.getValue())) { data.unbind(pos); }
        }
    }
    public static final class RefreshResult {
        public int immediate;
        public int deferred;
    }
    public static RefreshResult refresh(WorldServer world, ResourceLocationData data, ResourceLocationData.Location location) {
        pruneLoaded(world, data, location);
        if (location.chests().isEmpty()) { return null; }
        data.advance(location);
        RefreshResult result = new RefreshResult();
        for (BlockPos pos : new ArrayList<>(location.chests().keySet())) {
            ResourceChestTile tile = ResourceGuiHandler.find(world, pos);
            if (tile == null) { result.deferred++; }
            else { applyPending(tile); result.immediate++; }
        }
        return result;
    }
    public static void restart(WorldServer world, ResourceLocationData data) {
        data.selected = ""; data.remainingTicks = 0; data.warned = false; data.markDirty(); reconcile(world, data);
    }
    public static void reconcile(WorldServer world, ResourceLocationData data) {
        ResourceLocationData.Location current = data.find(data.selected);
        if (data.enabled && current != null && !current.chests().isEmpty()) { return; }
        data.selected = ""; data.remainingTicks = 0; data.warned = false;
        if (data.enabled) {
            List<ResourceLocationData.Location> eligible = data.eligible();
            if (!eligible.isEmpty()) {
                ResourceLocationData.Location next = eligible.get(world.rand.nextInt(eligible.size()));
                data.selected = ResourceLocationData.key(next.name); data.remainingTicks = data.intervalMinutes * 1200;
            }
        }
        data.markDirty();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getWorld(0) == null) { return; }
        WorldServer world = server.getWorld(0);
        ResourceLocationData data = ResourceLocationData.get(world);
        if (!data.enabled) { return; }
        ResourceLocationData.Location location = data.find(data.selected);
        if (location == null || location.chests().isEmpty()) {
            // Waiting does not scan locations every tick, and never sends notifications.
            if (server.getTickCounter() % 20 == 0) { reconcile(world, data); }
            return;
        }
        if (data.remainingTicks > 0) { data.remainingTicks--; }
        // Dirty once per simulated second; vanilla saves the current remaining ticks.
        if (data.remainingTicks % 20 == 0) { data.markDirty(); }
        if (data.remainingTicks > 0 && data.remainingTicks <= 1200 && !data.warned) {
            pruneLoaded(world, data, location);
            if (location.chests().isEmpty()) { restart(world, data); return; }
            data.warned = true; data.markDirty();
            CoreNetwork.CHANNEL.sendToAll(new ResourceNoticeMessage(location.name, true));
        }
        if (data.remainingTicks == 0) {
            RefreshResult result = refresh(world, data, location);
            if (result != null) { CoreNetwork.CHANNEL.sendToAll(new ResourceNoticeMessage(location.name, false)); }
            restart(world, data);
        }
    }
}