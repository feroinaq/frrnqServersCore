package com.frrnq.serverscore.video;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

final class VideoTestCommand extends CommandBase {
    private final VideoPlaybackManager video;

    VideoTestCommand(VideoPlaybackManager video) { this.video = video; }

    @Override public String getName() { return "fcorevideotest"; }
    @Override public String getUsage(ICommandSender sender) {
        return "/fcorevideotest <play file.mp4|stop|mode fullscreen/corner/overlay|opacity 0-100|volume 0-100|status>";
    }

    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        Minecraft mc = Minecraft.getMinecraft();
        IntegratedServer local = mc.getIntegratedServer();
        return sender == mc.player && mc.world != null && mc.isSingleplayer() && local != null
                && !local.getPublic() && local.getWorld(0) != null
                && local.getWorld(0).getWorldInfo().areCommandsAllowed();
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) { throw new WrongUsageException(getUsage(sender)); }
        if (video.remoteActive()) {
            throw new CommandException("A server video session is active.");
        }
        switch (args[0]) {
            case "play":
                if (args.length < 2) { break; }
                String file = buildString(args, 1);
                if (file.length() > 255 || !file.toLowerCase(Locale.ROOT).endsWith(".mp4")
                        || file.contains("/") || file.contains("\\") || file.contains(":")
                        || file.chars().anyMatch(Character::isISOControl)) {
                    throw new CommandException("Use an MP4 file name from frrnq_videos.");
                }
                video.play(file);
                return;
            case "stop":
                if (args.length != 1) { break; }
                video.stop();
                return;
            case "mode":
                if (args.length != 2) { break; }
                try {
                    video.setMode(VideoRenderer.Mode.valueOf(args[1].toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException failure) {
                    throw new CommandException("Choose fullscreen, corner or overlay.");
                }
                return;
            case "opacity":
                if (args.length != 2) { break; }
                video.setOpacity(parseInt(args[1], 0, 100));
                return;
            case "volume":
                if (args.length != 2) { break; }
                video.setVolume(parseInt(args[1], 0, 100));
                return;
            case "status":
                if (args.length != 1) { break; }
                sender.sendMessage(new TextComponentString(video.status()));
                return;
            default:
                break;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) { return getListOfStringsMatchingLastWord(args, "play", "stop", "mode", "opacity", "volume", "status"); }
        if (args.length == 2 && "mode".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "fullscreen", "corner", "overlay");
        }
        return Collections.emptyList();
    }
}
