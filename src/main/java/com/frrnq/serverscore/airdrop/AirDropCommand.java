package com.frrnq.serverscore.airdrop;

import java.util.Collections;
import java.util.List;
import com.frrnq.serverscore.network.AirDropEditorMessage;
import com.frrnq.serverscore.network.CoreNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

public final class AirDropCommand extends CommandBase {
    @Override public String getName() { return "airdrop"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/fcore airdrop give <template> | template list | template <create|edit|info|delete> <name>";
    }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, "fcore")) {
            throw new CommandException("commands.generic.permission");
        }
        AirDropData data = AirDropData.get(server);
        if (args.length == 3 && "give".equals(args[1])) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            AirDropData.Template template = require(data, args[2]);
            if (template.filled() == 0) { throw new CommandException("\u0428\u0430\u0431\u043b\u043e\u043d \u043f\u0443\u0441\u0442."); }
            int slot = player.inventory.getFirstEmptyStack();
            if (slot < 0) { throw new CommandException("\u041d\u0435\u0442 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u043e\u0433\u043e \u043c\u0435\u0441\u0442\u0430 \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435."); }
            player.inventory.setInventorySlotContents(slot, AirDropItem.prepared(template));
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            sender.sendMessage(new TextComponentString("\u0412\u044b\u0434\u0430\u043d AirDrop \u00b7 " + template.name));
            return;
        }
        if (args.length < 3 || !"template".equals(args[1])) { throw new WrongUsageException(getUsage(sender)); }
        if (args.length == 3 && "list".equals(args[2])) {
            List<String> names = data.names();
            sender.sendMessage(new TextComponentString(names.isEmpty() ? "\u0428\u0430\u0431\u043b\u043e\u043d\u043e\u0432 AirDrop \u043f\u043e\u043a\u0430 \u043d\u0435\u0442." : String.join(", ", names)));
            return;
        }
        if (args.length != 4) { throw new WrongUsageException(getUsage(sender)); }
        String name = args[3];
        switch (args[2]) {
            case "create":
                if (!AirDropData.validName(name)) { throw new CommandException("\u0418\u043c\u044f: 1\u201348 \u0431\u0443\u043a\u0432, \u0446\u0438\u0444\u0440, \u0434\u0435\u0444\u0438\u0441\u043e\u0432 \u0438\u043b\u0438 \u043f\u043e\u0434\u0447\u0451\u0440\u043a\u0438\u0432\u0430\u043d\u0438\u0439."); }
                if (data.find(name) != null) { throw new CommandException("\u0428\u0430\u0431\u043b\u043e\u043d \u0441 \u0442\u0430\u043a\u0438\u043c \u0438\u043c\u0435\u043d\u0435\u043c \u0443\u0436\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442."); }
                data.create(name);
                sender.sendMessage(new TextComponentString("\u0421\u043e\u0437\u0434\u0430\u043d \u0448\u0430\u0431\u043b\u043e\u043d " + name));
                break;
            case "edit":
                openEditor(getCommandSenderAsPlayer(sender), data, require(data, name));
                break;
            case "info":
                AirDropData.Template template = require(data, name);
                sender.sendMessage(new TextComponentString(template.name + " \u00b7 \u0437\u0430\u043f\u043e\u043b\u043d\u0435\u043d\u043e \u0441\u043b\u043e\u0442\u043e\u0432: " + template.filled() + " / 27"));
                break;
            case "delete":
                data.delete(require(data, name));
                sender.sendMessage(new TextComponentString("\u0423\u0434\u0430\u043b\u0451\u043d \u0448\u0430\u0431\u043b\u043e\u043d " + name));
                break;
            default: throw new WrongUsageException(getUsage(sender));
        }
    }

    private static AirDropData.Template require(AirDropData data, String name) throws CommandException {
        AirDropData.Template template = data.find(name);
        if (template == null) { throw new CommandException("\u0428\u0430\u0431\u043b\u043e\u043d \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d: " + name); }
        return template;
    }

    private static void openEditor(EntityPlayerMP player, AirDropData data, AirDropData.Template template) {
        if (player.openContainer != player.inventoryContainer) { player.closeScreen(); }
        player.getNextWindowId();
        AirDropEditorContainer editor = new AirDropEditorContainer(player.inventory, data, template);
        editor.windowId = player.currentWindowId;
        player.openContainer = editor;
        CoreNetwork.CHANNEL.sendTo(new AirDropEditorMessage(editor.windowId, template.name), player);
        editor.addListener(player);
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, editor));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 2) { return getListOfStringsMatchingLastWord(args, "template", "give"); }
        if (args.length == 3 && "template".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, "create", "edit", "list", "info", "delete");
        }
        if ((args.length == 3 && "give".equals(args[1]))
                || (args.length == 4 && "template".equals(args[1]) && !"create".equals(args[2]))) {
            return getListOfStringsMatchingLastWord(args, AirDropData.get(server).names());
        }
        return Collections.emptyList();
    }
}
