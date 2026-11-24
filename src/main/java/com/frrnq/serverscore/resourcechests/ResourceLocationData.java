package com.frrnq.serverscore.resourcechests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Overworld-owned metadata; no inventory contents or template settings are stored here. */
public final class ResourceLocationData extends WorldSavedData {
    private static final String KEY = "frrnqserverscore_resource_locations";
    public static final class Location {
        public final String name;
        public final UUID id;
        private long generation;
        private final Map<BlockPos, UUID> chests = new LinkedHashMap<>();
        private Location(String name, UUID id) { this.name = name; this.id = id; }
        public long generation() { return generation; }
        public Map<BlockPos, UUID> chests() { return Collections.unmodifiableMap(chests); }
    }
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final Map<BlockPos, Location> index = new LinkedHashMap<>();
    boolean enabled = true;
    int intervalMinutes = 6;
    int remainingTicks;
    String selected = "";
    boolean warned;

    public ResourceLocationData() { super(KEY); }
    public ResourceLocationData(String name) { super(name); }
    public static ResourceLocationData get(World overworld) {
        if (overworld.isRemote || overworld.provider.getDimension() != 0) { throw new IllegalArgumentException("Overworld server data required"); }
        MapStorage storage = overworld.getMapStorage();
        ResourceLocationData data = (ResourceLocationData) storage.getOrLoadData(ResourceLocationData.class, KEY);
        if (data == null) { data = new ResourceLocationData(); storage.setData(KEY, data); data.markDirty(); }
        return data;
    }
    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    public static boolean validName(String name) { return name.length() <= 48 && name.matches("[\\p{L}\\p{N}_-]{1,48}"); }
    public Location find(String name) { return locations.get(key(name)); }
    public Map<String, Location> locations() { return Collections.unmodifiableMap(locations); }
    public Location at(BlockPos pos) { return index.get(pos); }
    public List<Location> eligible() {
        List<Location> result = new ArrayList<>();
        for (Location location : locations.values()) { if (!location.chests.isEmpty()) { result.add(location); } }
        return result;
    }
    public void create(String name) {
        if (!validName(name) || find(name) != null) { throw new IllegalArgumentException("Invalid or duplicate location"); }
        locations.put(key(name), new Location(name, UUID.randomUUID())); markDirty();
    }
    public void delete(Location location) {
        for (BlockPos pos : location.chests.keySet()) { index.remove(pos); }
        locations.remove(key(location.name)); markDirty();
    }
    public void bind(Location location, ResourceChestTile tile) {
        if (index.containsKey(tile.getPos())) { throw new IllegalArgumentException("Chest already bound"); }
        UUID token = UUID.randomUUID();
        BlockPos pos = tile.getPos().toImmutable();
        location.chests.put(pos, token); index.put(pos, location);
        tile.assignLocation(location.id, token, location.generation);
        markDirty();
    }
    public boolean unbind(BlockPos pos) {
        Location old = index.remove(pos);
        if (old == null) { return false; }
        old.chests.remove(pos); markDirty(); return true;
    }
    public void advance(Location location) {
        if (location.generation == Long.MAX_VALUE) { throw new IllegalStateException("Resource generation counter exhausted"); }
        location.generation++; markDirty();
    }
    public void save(World world) { world.getMapStorage().saveAllData(); }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        locations.clear(); index.clear();
        enabled = !nbt.hasKey("Enabled") || nbt.getBoolean("Enabled");
        intervalMinutes = nbt.hasKey("IntervalMinutes") ? Math.max(2, Math.min(120, nbt.getInteger("IntervalMinutes"))) : 6;
        remainingTicks = Math.max(0, Math.min(intervalMinutes * 1200, nbt.getInteger("RemainingTicks")));
        selected = key(nbt.getString("Selected")); warned = nbt.getBoolean("Warned");
        NBTTagList entries = nbt.getTagList("Locations", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i); String name = entry.getString("Name");
            if (!validName(name) || locations.containsKey(key(name))) { continue; }
            Location location = new Location(name, entry.hasUniqueId("Id") ? entry.getUniqueId("Id") : UUID.randomUUID());
            location.generation = Math.max(0, entry.getLong("Generation"));
            locations.put(key(name), location);
            NBTTagList chests = entry.getTagList("Chests", 10);
            for (int j = 0; j < chests.tagCount(); j++) {
                NBTTagCompound chest = chests.getCompoundTagAt(j);
                BlockPos pos = BlockPos.fromLong(chest.getLong("Pos"));
                if (chest.getInteger("Dimension") != 0 || pos.getY() < 0 || pos.getY() > 255
                        || Math.abs((long) pos.getX()) >= 30000000 || Math.abs((long) pos.getZ()) >= 30000000
                        || !chest.hasUniqueId("Token") || index.containsKey(pos)) { continue; }
                location.chests.put(pos, chest.getUniqueId("Token")); index.put(pos, location);
            }
        }
        if (!enabled) { selected = ""; remainingTicks = 0; warned = false; }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("Enabled", enabled); nbt.setInteger("IntervalMinutes", intervalMinutes);
        nbt.setInteger("RemainingTicks", remainingTicks); nbt.setString("Selected", selected); nbt.setBoolean("Warned", warned);
        NBTTagList entries = new NBTTagList();
        for (Location location : locations.values()) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("Name", location.name);
            entry.setUniqueId("Id", location.id); entry.setLong("Generation", location.generation);
            NBTTagList chests = new NBTTagList();
            location.chests.forEach((pos, token) -> {
                NBTTagCompound chest = new NBTTagCompound(); chest.setInteger("Dimension", 0);
                chest.setLong("Pos", pos.toLong()); chest.setUniqueId("Token", token); chests.appendTag(chest);
            });
            entry.setTag("Chests", chests); entries.appendTag(entry);
        }
        nbt.setTag("Locations", entries); return nbt;
    }
}