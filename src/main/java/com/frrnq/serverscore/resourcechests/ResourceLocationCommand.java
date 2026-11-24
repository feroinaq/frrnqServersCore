package com.frrnq.serverscore.resourcechests;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

/** Extra resource subcommands; the existing chest edit/refill paths stay in ResourceCommand. */
public final class ResourceLocationCommand {
    private ResourceLocationCommand() {}
    private static void say(ICommandSender sender, String text) { sender.sendMessage(new TextComponentString(text)); }
    private static ResourceLocationData.Location require(ResourceLocationData data, String name) throws CommandException {
        ResourceLocationData.Location location = data.find(name);
        if (location == null) { throw new CommandException("\u041b\u043e\u043a\u0430\u0446\u0438\u044f \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430: %s", name); }
        return location;
    }
    public static void bind(EntityPlayerMP player, ResourceChestTile tile, String name) throws CommandException {
        if (!player.canUseCommand(2, "fcore") || !tile.isUsableByPlayer(player)
                || ResourceGuiHandler.find(player.world, tile.getPos()) != tile) { throw new CommandException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432 \u0438\u043b\u0438 \u0441\u0443\u043d\u0434\u0443\u043a \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d."); }
        if (player.dimension != 0) { throw new CommandException("\u041f\u0440\u0438\u0432\u044f\u0437\u043a\u0438 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044e\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0432 \u0438\u0437\u043c\u0435\u0440\u0435\u043d\u0438\u0438 0."); }
        WorldServer world = player.getServerWorld();
        ResourceLocationData data = ResourceLocationData.get(world);
        if (name == null) {
            boolean removed = ResourceLocations.unbind(world, tile.getPos()); data.save(world);
            say(player, removed ? "\u0421\u0443\u043d\u0434\u0443\u043a \u043e\u0442\u0432\u044f\u0437\u0430\u043d \u043e\u0442 \u043b\u043e\u043a\u0430\u0446\u0438\u0438." : "\u0421\u0443\u043d\u0434\u0443\u043a \u043d\u0435 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d \u043a \u043b\u043e\u043a\u0430\u0446\u0438\u0438."); return;
        }
        ResourceLocationData.Location location = require(data, name);
        ResourceLocationData.Location old = data.at(tile.getPos());
        if (old != null) {
            if (old == location) { say(player, "\u0421\u0443\u043d\u0434\u0443\u043a \u0443\u0436\u0435 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d \u043a \u043b\u043e\u043a\u0430\u0446\u0438\u0438 " + location.name + "."); return; }
            throw new CommandException("\u0421\u0443\u043d\u0434\u0443\u043a \u0443\u0436\u0435 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d \u043a %s. \u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 /fcore resource chest unbind.", old.name);
        }
        data.bind(location, tile); ResourceLocations.reconcile(world, data); data.save(world);
        say(player, "\u0421\u0443\u043d\u0434\u0443\u043a \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d \u043a \u043b\u043e\u043a\u0430\u0446\u0438\u0438 " + location.name + ". \u0422\u0435\u043a\u0443\u0449\u0438\u0439 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d.");
    }
    public static void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, "fcore")) { throw new CommandException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432."); }
        if (args.length < 3) { throw usage(); }
        WorldServer world = server.getWorld(0);
        ResourceLocationData data = ResourceLocationData.get(world);
        if ("timer".equals(args[1])) { timer(world, data, sender, args); return; }
        if (!"location".equals(args[1])) { throw usage(); }
        if ("list".equals(args[2]) && args.length == 3) {
            if (data.locations().isEmpty()) { say(sender, "\u0420\u0435\u0441\u0443\u0440\u0441\u043d\u044b\u0435 \u043b\u043e\u043a\u0430\u0446\u0438\u0438 \u043d\u0435 \u0441\u043e\u0437\u0434\u0430\u043d\u044b."); }
            for (ResourceLocationData.Location location : data.locations().values()) {
                say(sender, location.name + ": \u0441\u0443\u043d\u0434\u0443\u043a\u043e\u0432 " + location.chests().size());
            }
            return;
        }
        if (args.length != 4) { throw usage(); }
        if ("create".equals(args[2])) {
            if (!ResourceLocationData.validName(args[3])) { throw new CommandException("\u0418\u043c\u044f \u043b\u043e\u043a\u0430\u0446\u0438\u0438: 1\u201348 \u0431\u0443\u043a\u0432, \u0446\u0438\u0444\u0440, _ \u0438\u043b\u0438 -. \u0420\u0435\u0433\u0438\u0441\u0442\u0440 \u043d\u0435 \u0443\u0447\u0438\u0442\u044b\u0432\u0430\u0435\u0442\u0441\u044f."); }
            if (data.find(args[3]) != null) { throw new CommandException("\u041b\u043e\u043a\u0430\u0446\u0438\u044f \u0443\u0436\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442: %s", args[3]); }
            data.create(args[3]); data.save(world); say(sender, "\u0421\u043e\u0437\u0434\u0430\u043d\u0430 \u043b\u043e\u043a\u0430\u0446\u0438\u044f " + args[3] + "."); return;
        }
        ResourceLocationData.Location location = require(data, args[3]);
        switch (args[2]) {
            case "info":
                ResourceLocations.pruneLoaded(world, data, location); ResourceLocations.reconcile(world, data);
                say(sender, "\u041b\u043e\u043a\u0430\u0446\u0438\u044f " + location.name + ": \u0441\u0443\u043d\u0434\u0443\u043a\u043e\u0432 " + location.chests().size() + "; \u0441\u0442\u0430\u0442\u0443\u0441: "
                        + (location.chests().isEmpty() ? "\u043d\u0435\u0442 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d\u043d\u044b\u0445 \u0441\u0443\u043d\u0434\u0443\u043a\u043e\u0432" : "\u0433\u043e\u0442\u043e\u0432\u0430 \u043a \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044e") + "; \u0433\u0435\u043d\u0435\u0440\u0430\u0446\u0438\u044f " + location.generation());
                boolean selected = ResourceLocationData.key(location.name).equals(data.selected);
                say(sender, "\u0412\u044b\u0431\u0440\u0430\u043d\u0430 \u0434\u043b\u044f \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0433\u043e \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f: " + (selected ? "\u0434\u0430; \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c " + time(data.remainingTicks) : "\u043d\u0435\u0442"));
                for (BlockPos pos : location.chests().keySet()) {
                    say(sender, "dimension 0: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            + (world.isBlockLoaded(pos) ? " (\u0447\u0430\u043d\u043a \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d)" : " (\u0447\u0430\u043d\u043a \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d; \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 \u043e\u0442\u043b\u043e\u0436\u0435\u043d\u043e \u0434\u043e \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438)"));
                }
                return;
            case "delete":
                // Forget references only; neither inventories nor templates are touched.
                data.delete(location); ResourceLocations.reconcile(world, data); data.save(world);
                say(sender, "\u041b\u043e\u043a\u0430\u0446\u0438\u044f " + location.name + " \u0438 \u0435\u0451 \u043f\u0440\u0438\u0432\u044f\u0437\u043a\u0438 \u0443\u0434\u0430\u043b\u0435\u043d\u044b. \u0421\u0443\u043d\u0434\u0443\u043a\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b."); return;
            case "refresh":
                ResourceLocations.RefreshResult result = ResourceLocations.refresh(world, data, location);
                if (result == null) { ResourceLocations.reconcile(world, data); data.save(world); throw new CommandException("\u0412 \u043b\u043e\u043a\u0430\u0446\u0438\u0438 \u043d\u0435\u0442 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d\u043d\u044b\u0445 \u0440\u0435\u0441\u0443\u0440\u0441\u043d\u044b\u0445 \u0441\u0443\u043d\u0434\u0443\u043a\u043e\u0432."); }
                data.save(world);
                say(sender, "\u041b\u043e\u043a\u0430\u0446\u0438\u044f " + location.name + " \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430; \u0441\u0440\u0430\u0437\u0443: " + result.immediate + ", \u043f\u0440\u0438 \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0435 \u0447\u0430\u043d\u043a\u043e\u0432: " + result.deferred + "."); return;
            default: throw usage();
        }
    }
    private static void timer(WorldServer world, ResourceLocationData data, ICommandSender sender, String[] args) throws CommandException {
        if ("set".equals(args[2]) && args.length == 4) {
            int minutes;
            try { minutes = CommandBase.parseInt(args[3], 2, 120); }
            catch (CommandException invalid) { throw new CommandException("\u0418\u043d\u0442\u0435\u0440\u0432\u0430\u043b \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0446\u0435\u043b\u044b\u043c \u0447\u0438\u0441\u043b\u043e\u043c \u043e\u0442 2 \u0434\u043e 120 \u043c\u0438\u043d\u0443\u0442."); }
            data.intervalMinutes = minutes; ResourceLocations.restart(world, data); data.save(world);
            say(sender, "\u0418\u043d\u0442\u0435\u0440\u0432\u0430\u043b \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f: " + minutes + " \u043c\u0438\u043d. \u0422\u0435\u043a\u0443\u0449\u0438\u0439 \u0446\u0438\u043a\u043b \u0441\u0431\u0440\u043e\u0448\u0435\u043d."); return;
        }
        if (args.length != 3) { throw usage(); }
        switch (args[2]) {
            case "start":
                data.enabled = true; ResourceLocations.restart(world, data); data.save(world); say(sender, "\u0410\u0432\u0442\u043e\u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e. \u0417\u0430\u043f\u0443\u0449\u0435\u043d \u043d\u043e\u0432\u044b\u0439 \u0446\u0438\u043a\u043b."); return;
            case "stop":
                data.enabled = false; ResourceLocations.restart(world, data); data.save(world); say(sender, "\u0410\u0432\u0442\u043e\u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e. \u0422\u0435\u043a\u0443\u0449\u0438\u0439 \u0446\u0438\u043a\u043b \u0441\u0431\u0440\u043e\u0448\u0435\u043d."); return;
            case "info":
                ResourceLocations.reconcile(world, data);
                ResourceLocationData.Location selected = data.find(data.selected);
                say(sender, "\u0410\u0432\u0442\u043e\u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435: " + (data.enabled ? "\u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e" : "\u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e") + "; \u0438\u043d\u0442\u0435\u0440\u0432\u0430\u043b: " + data.intervalMinutes + " \u043c\u0438\u043d.; \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0445 \u043b\u043e\u043a\u0430\u0446\u0438\u0439: " + data.eligible().size());
                say(sender, selected == null ? "\u0412\u044b\u0431\u0440\u0430\u043d\u043d\u043e\u0439 \u043b\u043e\u043a\u0430\u0446\u0438\u0438 \u043d\u0435\u0442; \u0442\u0430\u0439\u043c\u0435\u0440 \u043e\u0436\u0438\u0434\u0430\u0435\u0442 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0443\u044e \u043b\u043e\u043a\u0430\u0446\u0438\u044e."
                        : "\u0412\u044b\u0431\u0440\u0430\u043d\u0430 " + selected.name + "; \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c " + time(data.remainingTicks)); return;
            default: throw usage();
        }
    }
    private static String time(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private static WrongUsageException usage() {
        return new WrongUsageException("/fcore resource location <create|info|delete|refresh> <\u0438\u043c\u044f> | location list | timer set <2..120> | timer <start|stop|info>");
    }
    public static List<String> completions(MinecraftServer server, String[] args) {
        if (args.length == 3 && "location".equals(args[1])) { return CommandBase.getListOfStringsMatchingLastWord(args, "create", "list", "info", "delete", "refresh"); }
        if (args.length == 3 && "timer".equals(args[1])) { return CommandBase.getListOfStringsMatchingLastWord(args, "set", "start", "stop", "info"); }
        if (args.length == 4 && (("location".equals(args[1]) && !"create".equals(args[2]))
                || ("chest".equals(args[1]) && "bind".equals(args[2])))) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ResourceLocationData.get(server.getWorld(0)).locations().keySet());
        }
        return Collections.emptyList();
    }
}