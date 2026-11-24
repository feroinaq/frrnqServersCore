package com.frrnq.serverscore.teamspawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;

/** No username lookup API: it can perform network requests and manufacture offline UUIDs. */
public final class KnownPlayers {
    private final Map<String, UUID> players = new HashMap<>();
    public KnownPlayers(MinecraftServer server) {
        Set<String> ambiguous = new HashSet<>();
        SimpleDateFormat expiryFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
        expiryFormat.setLenient(false);
        try (Reader reader = Files.newBufferedReader(server.getFile("usercache.json").toPath(), StandardCharsets.UTF_8)) {
            for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
                try {
                    String name = element.getAsJsonObject().get("name").getAsString();
                    UUID uuid = UUID.fromString(element.getAsJsonObject().get("uuid").getAsString());
                    // Expired name bindings may refer to a former owner: require a fresh cache entry.
                    Date expiry;
                    try {
                        expiry = expiryFormat.parse(element.getAsJsonObject().get("expiresOn").getAsString());
                    } catch (java.text.ParseException invalidDate) { continue; }
                    if (!expiry.after(new Date())) { continue; }
                    EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
                    if (online != null && !name.equalsIgnoreCase(online.getGameProfile().getName())) { continue; }
                    GameProfile cached = server.getPlayerProfileCache().getProfileByUUID(uuid);
                    if (cached != null && name.equalsIgnoreCase(cached.getName())) {
                        String key = name.toLowerCase(Locale.ROOT);
                        UUID previous = players.putIfAbsent(key, uuid);
                        if (previous != null && !previous.equals(uuid)) { ambiguous.add(key); }
                    }
                } catch (RuntimeException malformedEntry) { /* Ignore invalid cache entries. */ }
            }
        } catch (java.io.IOException | RuntimeException exception) {
            LogManager.getLogger("frrnqserverscore/TeamSpawn").warn("Cannot read local player cache; only online players can be resolved.", exception);
        }
        for (String name : ambiguous) { players.remove(name); }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            players.put(player.getGameProfile().getName().toLowerCase(Locale.ROOT), player.getGameProfile().getId());
        }
    }
    public UUID resolve(String nickname) { return players.get(nickname.toLowerCase(Locale.ROOT)); }
    public static String name(MinecraftServer server, UUID uuid) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) { return online.getName(); }
        GameProfile cached = server.getPlayerProfileCache().getProfileByUUID(uuid);
        return cached != null && cached.getName() != null ? cached.getName() : uuid.toString();
    }
}