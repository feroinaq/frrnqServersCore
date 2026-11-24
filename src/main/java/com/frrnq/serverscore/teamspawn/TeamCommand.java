package com.frrnq.serverscore.teamspawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import com.frrnq.serverscore.death.DeathRespawn;

public final class TeamCommand extends CommandBase {
    @Override public String getName() { return "team"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/fcore team create <\u0438\u043c\u044f> | spawn <\u043a\u043e\u043c\u0430\u043d\u0434\u0430> <x> <y> <z> | add <\u043a\u043e\u043c\u0430\u043d\u0434\u0430> <\u043d\u0438\u043a1;\u043d\u0438\u043a2> | remove <\u043d\u0438\u043a> | list | info <\u043a\u043e\u043c\u0430\u043d\u0434\u0430> | start";
    }
    private void message(ICommandSender sender, String text) { sender.sendMessage(new TextComponentString(text)); }
    private String team(TeamData data, String input) throws CommandException {
        String name = input.toLowerCase(Locale.ROOT);
        if (!data.teams().containsKey(name)) { throw new CommandException("\u041a\u043e\u043c\u0430\u043d\u0434\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430: %s", input); }
        return name;
    }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) { throw new WrongUsageException(getUsage(sender)); }
        TeamData data = TeamData.get(server);
        switch (args[1]) {
            case "create": {
                if (args.length != 3) { throw new WrongUsageException(getUsage(sender)); }
                String name = args[2].toLowerCase(Locale.ROOT);
                if (!name.matches("[a-z0-9_-]{1,32}")) { throw new CommandException("\u0418\u043c\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u044b: 1\u201332 \u0441\u0438\u043c\u0432\u043e\u043b\u0430, \u043b\u0430\u0442\u0438\u043d\u0438\u0446\u0430, \u0446\u0438\u0444\u0440\u044b, _ \u0438\u043b\u0438 -."); }
                if (data.teams().containsKey(name)) { throw new CommandException("\u041a\u043e\u043c\u0430\u043d\u0434\u0430 \u0443\u0436\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442: %s", name); }
                data.create(name); data.save(server); message(sender, "\u0421\u043e\u0437\u0434\u0430\u043d\u0430 \u043a\u043e\u043c\u0430\u043d\u0434\u0430: " + name); return;
            }
            case "spawn": {
                if (args.length != 6) { throw new WrongUsageException(getUsage(sender)); }
                String name = team(data, args[2]);
                BlockPos pos;
                try {
                    pos = new BlockPos(parseInt(args[3], -29999984, 29999983), parseInt(args[4], 1, 254), parseInt(args[5], -29999984, 29999983));
                } catch (CommandException invalid) { throw new CommandException("\u041a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b \u0434\u043e\u043b\u0436\u043d\u044b \u0431\u044b\u0442\u044c \u0446\u0435\u043b\u044b\u043c\u0438: X/Z \u0432 \u043f\u0440\u0435\u0434\u0435\u043b\u0430\u0445 \u043c\u0438\u0440\u0430, Y \u043e\u0442 1 \u0434\u043e 254."); }
                if (!DeathRespawn.isSafe(server.getWorld(0), pos)) { throw new CommandException("\u041d\u0435\u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u0430\u044f \u0442\u043e\u0447\u043a\u0430: \u043d\u0443\u0436\u0435\u043d \u0442\u0432\u0451\u0440\u0434\u044b\u0439 \u043f\u043e\u043b, \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u043e\u0435 \u043c\u0435\u0441\u0442\u043e \u043d\u0430\u0434 \u043d\u0438\u043c \u0438 \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 \u043e\u043f\u0430\u0441\u043d\u043e\u0441\u0442\u0435\u0439."); }
                data.setSpawn(name, pos); data.save(server); message(sender, "\u0421\u043f\u0430\u0432\u043d \u043a\u043e\u043c\u0430\u043d\u0434\u044b " + name + ": " + coords(pos)); return;
            }
            case "add": {
                if (args.length != 4) { throw new WrongUsageException(getUsage(sender)); }
                String name = team(data, args[2]);
                KnownPlayers players = new KnownPlayers(server);
                for (String nick : args[3].split(";", -1)) {
                    if (!TeamData.validNickname(nick)) {
                        message(sender, "\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u043d\u0438\u043a: '" + nick + "'. \u0414\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b 3\u201316 \u0441\u0438\u043c\u0432\u043e\u043b\u043e\u0432: \u043b\u0430\u0442\u0438\u043d\u0438\u0446\u0430, \u0446\u0438\u0444\u0440\u044b, _."); continue;
                    }
                    String waiting = data.pendingTeam(nick);
                    if (waiting != null) { message(sender, nick + " \u0443\u0436\u0435 \u043e\u0436\u0438\u0434\u0430\u0435\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 " + waiting); continue; }
                    UUID uuid = players.resolve(nick);
                    if (uuid == null) {
                        data.addPending(nick, name);
                        message(sender, nick + " \u043e\u0436\u0438\u0434\u0430\u0435\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 " + name + " \u043f\u0440\u0438 \u0432\u0445\u043e\u0434\u0435. \u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0437\u0430\u043a\u0440\u0435\u043f\u0438\u0442\u0441\u044f \u0437\u0430 \u043f\u0440\u043e\u0444\u0438\u043b\u0435\u043c \u0441 \u044d\u0442\u0438\u043c \u0438\u043c\u0435\u043d\u0435\u043c.");
                        continue;
                    }
                    String current = data.team(uuid);
                    if (current != null) { message(sender, nick + " \u0443\u0436\u0435 \u0441\u043e\u0441\u0442\u043e\u0438\u0442 \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0435 " + current + ". \u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 remove."); continue; }
                    data.add(uuid, name); message(sender, nick + " \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 " + name);
                }
                data.save(server); return;
            }
            case "remove": {
                if (args.length != 3) { throw new WrongUsageException(getUsage(sender)); }
                if (!TeamData.validNickname(args[2])) { throw new CommandException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u043d\u0438\u043a: \u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b 3\u201316 \u0441\u0438\u043c\u0432\u043e\u043b\u043e\u0432 \u043b\u0430\u0442\u0438\u043d\u0438\u0446\u044b, \u0446\u0438\u0444\u0440 \u0438 _."); }
                // Resolve pending conflicts without removing a confirmed UUID assignment.
                if (data.removePending(args[2])) {
                    data.save(server); message(sender, "\u041e\u0436\u0438\u0434\u0430\u044e\u0449\u0435\u0435 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0443\u0434\u0430\u043b\u0435\u043d\u043e: " + args[2]); return;
                }
                UUID uuid = new KnownPlayers(server).resolve(args[2]);
                if (uuid == null) { throw new CommandException("UUID \u0438\u0433\u0440\u043e\u043a\u0430 \u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u0435\u043d, \u043e\u0436\u0438\u0434\u0430\u044e\u0449\u0435\u0435 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442: %s", args[2]); }
                boolean removed = data.remove(uuid); data.save(server);
                message(sender, removed ? "\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0451\u043d\u043d\u044b\u0439 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a \u0443\u0434\u0430\u043b\u0451\u043d: " + args[2] : "\u0418\u0433\u0440\u043e\u043a \u043d\u0435 \u0441\u043e\u0441\u0442\u043e\u0438\u0442 \u0432 \u043a\u043e\u043c\u0430\u043d\u0434\u0435: " + args[2]); return;
            }
            case "list": {
                if (args.length != 2) { throw new WrongUsageException(getUsage(sender)); }
                message(sender, "\u041a\u043e\u043c\u0430\u043d\u0434\u044b: " + (data.teams().isEmpty() ? "\u043d\u0435 \u0441\u043e\u0437\u0434\u0430\u043d\u044b" : String.join(", ", data.teams().keySet()))); return;
            }
            case "info": {
                if (args.length != 3) { throw new WrongUsageException(getUsage(sender)); }
                String name = team(data, args[2]);
                List<String> members = data.members().entrySet().stream().filter(e -> name.equals(e.getValue()))
                        .map(e -> KnownPlayers.name(server, e.getKey())).collect(Collectors.toList());
                message(sender, "\u041a\u043e\u043c\u0430\u043d\u0434\u0430 " + name + "; \u0441\u043f\u0430\u0432\u043d: " + coords(data.teams().get(name)) + "; \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u043e\u0432: " + members.size());
                message(sender, "\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0451\u043d\u043d\u044b\u0435 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438: " + (members.isEmpty() ? "\u043d\u0435\u0442" : String.join(", ", members)));
                List<String> waiting = data.pending().entrySet().stream().filter(e -> name.equals(e.getValue()))
                        .map(e -> e.getKey()).collect(Collectors.toList());
                message(sender, "\u041e\u0436\u0438\u0434\u0430\u044e\u0449\u0438\u0435 \u0443\u0447\u0430\u0441\u0442\u043d\u0438\u043a\u0438 (" + waiting.size() + "): " + (waiting.isEmpty() ? "\u043d\u0435\u0442" : String.join(", ", waiting))); return;
            }
            case "start": {
                if (args.length != 2) { throw new WrongUsageException(getUsage(sender)); }
                Set<BlockPos> reserved = new HashSet<>(); int count = 0;
                // Snapshot: dimension transfer may modify the server player list.
                for (EntityPlayerMP player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                    String name = data.team(player.getUniqueID());
                    if (name == null) { continue; }
                    BlockPos center = data.teams().get(name);
                    if (center == null) { message(sender, "\u041f\u0440\u043e\u043f\u0443\u0449\u0435\u043d " + player.getName() + ": \u0443 \u043a\u043e\u043c\u0430\u043d\u0434\u044b " + name + " \u043d\u0435\u0442 \u0441\u043f\u0430\u0432\u043d\u0430."); continue; }
                    if (!player.isEntityAlive()) { message(sender, "\u041f\u0440\u043e\u043f\u0443\u0449\u0435\u043d " + player.getName() + ": \u0438\u0433\u0440\u043e\u043a \u043c\u0451\u0440\u0442\u0432."); continue; }
                    BlockPos pos = TeamSpawn.findPosition(player, center, reserved);
                    if (pos == null) { message(sender, "\u041f\u0440\u043e\u043f\u0443\u0449\u0435\u043d " + player.getName() + ": \u0440\u044f\u0434\u043e\u043c \u0441\u043e \u0441\u043f\u0430\u0432\u043d\u043e\u043c \u043d\u0435\u0442 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e\u0433\u043e \u043c\u0435\u0441\u0442\u0430."); continue; }
                    TeamSpawn.teleport(player, pos); reserved.add(pos); count++;
                }
                message(sender, "\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u043e \u0438\u0433\u0440\u043e\u043a\u043e\u0432: " + count); return;
            }
            default: throw new WrongUsageException(getUsage(sender));
        }
    }
    private static String coords(BlockPos pos) {
        return pos == null ? "\u043d\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043d" : pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (\u0438\u0437\u043c\u0435\u0440\u0435\u043d\u0438\u0435 0)";
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 2) { return getListOfStringsMatchingLastWord(args, "create", "spawn", "add", "remove", "list", "info", "start"); }
        if (args.length == 3 && ("spawn".equals(args[1]) || "add".equals(args[1]) || "info".equals(args[1]))) {
            return getListOfStringsMatchingLastWord(args, TeamData.get(server).teams().keySet());
        }
        if (args.length == 3 && "remove".equals(args[1])) {
            Set<String> names = new HashSet<>(TeamData.get(server).pending().keySet());
            Collections.addAll(names, server.getPlayerProfileCache().getUsernames());
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}