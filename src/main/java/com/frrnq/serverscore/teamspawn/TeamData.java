package com.frrnq.serverscore.teamspawn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class TeamData extends WorldSavedData {
    private static final String KEY = "frrnqserverscore_teams";
    private final Map<String, BlockPos> teams = new LinkedHashMap<>();
    private final Map<UUID, String> membership = new LinkedHashMap<>();
    private final Map<String, String> pending = new LinkedHashMap<>();
    public TeamData() { super(KEY); }
    public TeamData(String name) { super(name); }
    public static TeamData get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        TeamData data = (TeamData) storage.getOrLoadData(TeamData.class, KEY);
        if (data == null) { data = new TeamData(); storage.setData(KEY, data); data.markDirty(); }
        return data;
    }
    public Map<String, BlockPos> teams() { return Collections.unmodifiableMap(teams); }
    public Map<UUID, String> members() { return Collections.unmodifiableMap(membership); }
    public Map<String, String> pending() { return Collections.unmodifiableMap(pending); }
    public static String normalize(String nickname) { return nickname.toLowerCase(Locale.ROOT); }
    public static boolean validNickname(String nickname) { return nickname.matches("[A-Za-z0-9_]{3,16}"); }
    public String pendingTeam(String nickname) { return pending.get(normalize(nickname)); }
    public void addPending(String nickname, String team) {
        if (!validNickname(nickname) || !teams.containsKey(team)) { throw new IllegalArgumentException("Invalid pending assignment"); }
        String key = normalize(nickname);
        if (pending.containsKey(key)) { throw new IllegalArgumentException("Already pending"); }
        pending.put(key, team); markDirty();
    }
    public boolean removePending(String nickname) {
        boolean removed = pending.remove(normalize(nickname)) != null;
        if (removed) { markDirty(); }
        return removed;
    }
    public String team(UUID uuid) { return membership.get(uuid); }
    public BlockPos spawn(UUID uuid) { return teams.get(team(uuid)); }
    public void create(String name) {
        if (teams.containsKey(name)) { throw new IllegalArgumentException("Duplicate team"); }
        teams.put(name, null); markDirty();
    }
    public void setSpawn(String name, BlockPos pos) { teams.put(name, pos.toImmutable()); markDirty(); }
    public void add(UUID uuid, String name) {
        if (!teams.containsKey(name) || (membership.containsKey(uuid) && !name.equals(membership.get(uuid)))) {
            throw new IllegalArgumentException("Conflicting team assignment");
        }
        membership.put(uuid, name); markDirty();
    }
    public boolean remove(UUID uuid) { boolean removed = membership.remove(uuid) != null; if (removed) { markDirty(); } return removed; }
    public void save(MinecraftServer server) { server.getWorld(0).getMapStorage().saveAllData(); }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        teams.clear(); membership.clear(); pending.clear();
        NBTTagList list = nbt.getTagList("teams", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String name = entry.getString("name");
            if (name.matches("[a-z0-9_-]{1,32}")) {
                teams.put(name, entry.hasKey("spawn", 4) ? BlockPos.fromLong(entry.getLong("spawn")) : null);
            }
        }
        list = nbt.getTagList("members", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.hasUniqueId("uuid") && teams.containsKey(entry.getString("team"))) {
                membership.put(entry.getUniqueId("uuid"), entry.getString("team"));
            }
        }
        // Old saves have no pending list; existing teams/members retain their format.
        list = nbt.getTagList("pending", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String nickname = entry.getString("nickname");
            String team = entry.getString("team");
            if (validNickname(nickname) && teams.containsKey(team)) {
                pending.putIfAbsent(normalize(nickname), team);
            }
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        teams.forEach((name, spawn) -> {
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("name", name);
            if (spawn != null) { entry.setLong("spawn", spawn.toLong()); }
            list.appendTag(entry);
        });
        nbt.setTag("teams", list);
        NBTTagList players = new NBTTagList();
        membership.forEach((uuid, name) -> {
            NBTTagCompound entry = new NBTTagCompound(); entry.setUniqueId("uuid", uuid); entry.setString("team", name);
            players.appendTag(entry);
        });
        nbt.setTag("members", players);
        NBTTagList waiting = new NBTTagList();
        pending.forEach((nickname, team) -> {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("nickname", nickname); entry.setString("team", team);
            waiting.appendTag(entry);
        });
        nbt.setTag("pending", waiting);
        return nbt;
    }
}