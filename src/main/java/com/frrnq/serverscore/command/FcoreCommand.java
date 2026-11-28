package com.frrnq.serverscore.command;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import com.frrnq.serverscore.death.DeathCommand;

/** One root command preserves both modules and their existing argument contracts. */
public final class FcoreCommand extends CommandBase {
    private final com.frrnq.serverscore.resourcechests.ResourceCommand resources = new com.frrnq.serverscore.resourcechests.ResourceCommand();
    private final com.frrnq.serverscore.airdrop.AirDropCommand airdrops = new com.frrnq.serverscore.airdrop.AirDropCommand();
    private final com.frrnq.serverscore.video.VideoCommand video = new com.frrnq.serverscore.video.VideoCommand();
    private final CommandBase world;
    private final DeathCommand deaths = new DeathCommand();
    private final com.frrnq.serverscore.teamspawn.TeamCommand teams = new com.frrnq.serverscore.teamspawn.TeamCommand();
    public FcoreCommand(CommandBase world) { this.world = world; }
    @Override public String getName() { return "fcore"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) { return "/fcore <world|deaths|team|resource|airdrop|video> ..."; }
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 0 && "world".equals(args[0])) { world.execute(server, sender, args); }
        else if (args.length > 0 && "deaths".equals(args[0])) { deaths.execute(server, sender, args); }
        else if (args.length > 0 && "team".equals(args[0])) { teams.execute(server, sender, args); }
        else if (args.length > 0 && "resource".equals(args[0])) { resources.execute(server, sender, args); }
        else if (args.length > 0 && "airdrop".equals(args[0])) { airdrops.execute(server, sender, args); }
        else if (args.length > 0 && "video".equals(args[0])) { video.execute(server, sender, args); }
        else { throw new WrongUsageException(getUsage(sender)); }
    }
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) { return getListOfStringsMatchingLastWord(args, "world", "deaths", "team", "resource", "airdrop", "video"); }
        if (args.length > 1 && "world".equals(args[0])) { return world.getTabCompletions(server, sender, args, pos); }
        if (args.length > 1 && "deaths".equals(args[0])) { return deaths.getTabCompletions(server, sender, args, pos); }
        if (args.length > 1 && "team".equals(args[0])) { return teams.getTabCompletions(server, sender, args, pos); }
        if (args.length > 1 && "resource".equals(args[0])) { return resources.getTabCompletions(server, sender, args, pos); }
        if (args.length > 1 && "airdrop".equals(args[0])) { return airdrops.getTabCompletions(server, sender, args, pos); }
        if (args.length > 1 && "video".equals(args[0])) { return video.getTabCompletions(server, sender, args, pos); }
        return Collections.emptyList();
    }
}
