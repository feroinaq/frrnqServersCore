package com.frrnq.serverscore.death;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** Receives the complete fcore argument list, including the deaths branch. */
public final class DeathCommand extends CommandBase {
    @Override public String getName() { return "deaths"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/fcore deaths setlimit <1..100> | setspawn <x> <y> <z> | setplayer <nickname> <lives> | reset <nickname> | info <nickname>";
    }
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) { throw new WrongUsageException(getUsage(sender)); }
        DeathData data = DeathData.get(server);
        switch (args[1]) {
            case "setlimit":
                if (args.length != 3) { throw new WrongUsageException(getUsage(sender)); }
                data.setLimit(parseInt(args[2], 1, 100));
                break;
            case "setspawn":
                if (args.length != 5) { throw new WrongUsageException(getUsage(sender)); }
                BlockPos pos = new BlockPos(parseInt(args[2], -29999984, 29999983),
                        parseInt(args[3], 1, 254), parseInt(args[4], -29999984, 29999983));
                if (!DeathRespawn.isSafe(server.getWorld(0), pos)) {
                    throw new CommandException("Unsafe spawn: use a solid floor and two empty blocks above it, away from hazards, inside the world border.");
                }
                data.setSpawn(pos);
                break;
            case "setplayer":
            case "reset":
            case "info":
                int expected = "setplayer".equals(args[1]) ? 4 : 3;
                if (args.length != expected) { throw new WrongUsageException(getUsage(sender)); }
                EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[2]);
                if (target == null) { throw new CommandException("Player must be online: %s", args[2]); }
                if ("info".equals(args[1])) {
                    int remaining = data.getLives(target.getUniqueID());
                    BlockPos spawn = data.getSpawn();
                    sender.sendMessage(new TextComponentString("DeathLimit: " + target.getName() + ", lives=" + remaining
                            + "/" + data.getLimit() + ", status=" + (remaining == 0 ? "eliminated" : "active")
                            + ", spawn=" + (spawn == null ? "not configured" : spawn.getX() + " " + spawn.getY() + " " + spawn.getZ() + " (dimension 0)")));
                    return;
                }
                data.setLives(target.getUniqueID(), "reset".equals(args[1]) ? data.getLimit() : parseInt(args[3], 0, data.getLimit()));
                break;
            default: throw new WrongUsageException(getUsage(sender));
        }
        data.save(server);
        sender.sendMessage(new TextComponentString("DeathLimit updated. Use /fcore deaths info <nickname> to inspect player state."));
    }
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 2) { return getListOfStringsMatchingLastWord(args, "setlimit", "setspawn", "setplayer", "reset", "info"); }
        if (args.length == 3 && ("setplayer".equals(args[1]) || "reset".equals(args[1]) || "info".equals(args[1]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}