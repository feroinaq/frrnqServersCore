package com.frrnq.serverscore.death;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

public final class DeathData extends WorldSavedData {
    private static final String NAME = "frrnqserverscore_death";
    private final Map<UUID, Integer> lives = new HashMap<>();
    private int limit = 5;
    private BlockPos spawn;

    public DeathData() { super(NAME); }
    public DeathData(String name) { super(name); }

    public static DeathData get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        DeathData data = (DeathData) storage.getOrLoadData(DeathData.class, NAME);
        if (data == null) {
            data = new DeathData();
            storage.setData(NAME, data);
            data.markDirty();
        }
        return data;
    }

    public int getLives(UUID uuid) {
        if (!lives.containsKey(uuid)) {
            lives.put(uuid, limit);
            markDirty();
        }
        return lives.get(uuid);
    }
    /** Reads existing lives without initializing a player record. */
    public int peekLives(UUID uuid) { return lives.getOrDefault(uuid, limit); }
    public int getLimit() { return limit; }
    public BlockPos getSpawn() { return spawn; }
    public void setLives(UUID uuid, int value) {
        if (value < 0 || value > limit) { throw new IllegalArgumentException("Invalid lives"); }
        lives.put(uuid, value);
        markDirty();
    }
    public void setLimit(int value) {
        if (value < 1 || value > 100) { throw new IllegalArgumentException("Invalid limit"); }
        limit = value;
        lives.replaceAll((uuid, remaining) -> Math.min(remaining, limit));
        markDirty();
    }
    public void setSpawn(BlockPos value) { spawn = value.toImmutable(); markDirty(); }
    public void save(MinecraftServer server) {
        server.getWorld(0).getMapStorage().saveAllData();
        com.frrnq.serverscore.network.DeathSync.changed(server);
        com.frrnq.serverscore.network.TeamStatusSync.changed();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        lives.clear();
        limit = nbt.hasKey("limit") ? Math.max(1, Math.min(100, nbt.getInteger("limit"))) : 5;
        spawn = nbt.hasKey("spawn", 10) ? BlockPos.fromLong(nbt.getCompoundTag("spawn").getLong("pos")) : null;
        NBTTagList players = nbt.getTagList("players", 10);
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            if (player.hasUniqueId("uuid")) {
                lives.put(player.getUniqueId("uuid"), Math.max(0, Math.min(limit, player.getInteger("lives"))));
            }
        }
    }
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("limit", limit);
        NBTTagList players = new NBTTagList();
        lives.forEach((uuid, remaining) -> {
            NBTTagCompound player = new NBTTagCompound();
            player.setUniqueId("uuid", uuid);
            player.setInteger("lives", remaining);
            players.appendTag(player);
        });
        nbt.setTag("players", players);
        if (spawn != null) {
            NBTTagCompound point = new NBTTagCompound();
            point.setLong("pos", spawn.toLong());
            nbt.setTag("spawn", point);
        } else { nbt.removeTag("spawn"); }
        return nbt;
    }
}
