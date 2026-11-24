package com.frrnq.serverscore.resourcechests;

import java.util.List;
import com.frrnq.serverscore.FrrnqServersCore;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

public final class ResourceCommand extends CommandBase {
    @Override public String getName() { return "resource"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) { return "/fcore resource chest <edit|refill|bind|unbind> | location <create|list|info|delete|refresh> | timer <set|start|stop|info>"; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, "fcore")) { throw new CommandException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432."); }
        if (args.length < 2) { throw new WrongUsageException(getUsage(sender)); }
        if ("location".equals(args[1]) || "timer".equals(args[1])) {
            ResourceLocationCommand.execute(server, sender, args); return;
        }
        boolean binding = args.length == 4 && "bind".equals(args[2]);
        if (!"chest".equals(args[1]) || (!binding && (args.length != 3
                || !("edit".equals(args[2]) || "refill".equals(args[2]) || "unbind".equals(args[2]))))) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        Vec3d eye = player.getPositionEyes(1), end = eye.add(player.getLook(1).scale(6));
        // Check every chunk in the ray's bounding rectangle before ray tracing.
        int minX = net.minecraft.util.math.MathHelper.floor(Math.min(eye.x, end.x)) >> 4;
        int maxX = net.minecraft.util.math.MathHelper.floor(Math.max(eye.x, end.x)) >> 4;
        int minZ = net.minecraft.util.math.MathHelper.floor(Math.min(eye.z, end.z)) >> 4;
        int maxZ = net.minecraft.util.math.MathHelper.floor(Math.max(eye.z, end.z)) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!player.world.isBlockLoaded(new BlockPos(x << 4, 64, z << 4))) {
                    throw new CommandException("\u0426\u0435\u043b\u044c \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u0440\u044f\u0434\u043e\u043c \u0441 \u043d\u0435\u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u043c \u0447\u0430\u043d\u043a\u043e\u043c.");
                }
            }
        }
        RayTraceResult hit = player.world.rayTraceBlocks(eye, end, false, true, false);
        ResourceChestTile tile = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? ResourceGuiHandler.find(player.world, hit.getBlockPos()) : null;
        if (tile == null || !tile.isUsableByPlayer(player)) { throw new CommandException("\u041f\u043e\u0441\u043c\u043e\u0442\u0440\u0438\u0442\u0435 \u043d\u0430 \u0440\u0435\u0441\u0443\u0440\u0441\u043d\u044b\u0439 \u0441\u0443\u043d\u0434\u0443\u043a \u0441 \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u044f \u0434\u043e 6 \u0431\u043b\u043e\u043a\u043e\u0432."); }
        if (binding || "unbind".equals(args[2])) {
            ResourceLocationCommand.bind(player, tile, binding ? args[3] : null); return;
        }
        if ("edit".equals(args[2])) { BlockPos pos = tile.getPos(); player.openGui(FrrnqServersCore.instance, 0, player.world, pos.getX(), pos.getY(), pos.getZ()); }
        else { tile.refill(); player.sendMessage(new TextComponentString("\u0420\u0435\u0441\u0443\u0440\u0441\u043d\u044b\u0439 \u0441\u0443\u043d\u0434\u0443\u043a \u043e\u0431\u043d\u043e\u0432\u043b\u0451\u043d: \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0430 \u043d\u043e\u0432\u0430\u044f \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u0430\u044f \u0433\u0435\u043d\u0435\u0440\u0430\u0446\u0438\u044f.")); }
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 2) { return getListOfStringsMatchingLastWord(args, "chest", "location", "timer"); }
        if (args.length == 3 && "chest".equals(args[1])) { return getListOfStringsMatchingLastWord(args, "edit", "refill", "bind", "unbind"); }
        return ResourceLocationCommand.completions(server, args);
    }
}