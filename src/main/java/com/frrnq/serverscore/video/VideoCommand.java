package com.frrnq.serverscore.video;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public final class VideoCommand extends CommandBase {
    static final List<String> MODES = Collections.unmodifiableList(Arrays.asList("fullscreen", "corner", "overlay"));

    @Override public String getName() { return "video"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/fcore video <play URL|preload URL|start|stop|mode fullscreen/corner/overlay|opacity 0-100|volume 0-100|status>";
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, "fcore")) { throw new CommandException("commands.generic.permission"); }
        if (args.length < 2) { throw new WrongUsageException(getUsage(sender)); }
        switch (args[1]) {
            case "play":
            case "preload":
                if (args.length != 3) { break; }
                URI url;
                try { url = VideoDomains.check(args[2]); }
                catch (IllegalArgumentException invalid) { throw new CommandException(invalid.getMessage()); }
                VideoSessions.prepare(url, sender, "play".equals(args[1]));
                reply(sender, "Preparing video from " + url.getHost() + ".");
                return;
            case "start":
                if (args.length != 2) { break; }
                if (!VideoSessions.requestStart(sender)) { throw new CommandException("No preloaded video is waiting for start."); }
                reply(sender, "Video start requested.");
                return;
            case "stop":
                if (args.length != 2) { break; }
                reply(sender, VideoSessions.stop() ? "Video session stopped." : "No active video session.");
                return;
            case "mode":
                if (args.length != 3) { break; }
                int mode = MODES.indexOf(args[2].toLowerCase(Locale.ROOT));
                if (mode < 0) { throw new CommandException("Choose fullscreen, corner or overlay."); }
                VideoSessions.setMode(mode);
                reply(sender, "Video mode: " + MODES.get(mode) + ".");
                return;
            case "opacity":
            case "volume":
                if (args.length != 3) { break; }
                int percent;
                try { percent = Integer.parseInt(args[2]); }
                catch (NumberFormatException invalid) { throw new CommandException("Enter a percentage from 0 to 100."); }
                if (percent < 0 || percent > 100) { throw new CommandException("Enter a percentage from 0 to 100."); }
                if ("opacity".equals(args[1])) { VideoSessions.setOpacity(percent); }
                else { VideoSessions.setVolume(percent); }
                reply(sender, "Video " + args[1] + ": " + percent + "%.");
                return;
            case "status":
                if (args.length != 2) { break; }
                reply(sender, VideoSessions.status(server));
                return;
            default: break;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    private void reply(ICommandSender sender, String message) { sender.sendMessage(new TextComponentString(message)); }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 2) { return getListOfStringsMatchingLastWord(args, "play", "preload", "start", "stop", "mode", "opacity", "volume", "status"); }
        if (args.length == 3 && "mode".equals(args[1])) { return getListOfStringsMatchingLastWord(args, MODES); }
        return Collections.emptyList();
    }
}
