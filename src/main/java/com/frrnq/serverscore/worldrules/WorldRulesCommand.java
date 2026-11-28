package com.frrnq.serverscore.worldrules;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public final class WorldRulesCommand extends CommandBase {
    private final WorldRulesConfig settings;

    public WorldRulesCommand(WorldRulesConfig settings) {
        this.settings = settings;
    }

    @Override
    public String getName() { return "fcore"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/fcore world info | /fcore world buildmin <y: 0..255> | /fcore world buildmax <y: 0..255> | /fcore world pvpmax <y: 0..255>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && "world".equals(args[0]) && "info".equals(args[1])) {
            sender.sendMessage(new TextComponentString("WorldRules: minBuildY=" + settings.getMinBuildY()
                    + ", maxBuildY=" + settings.getMaxBuildY() + ", maxPvpY=" + settings.getMaxPvpY() + ", dimensionId=" + settings.getDimensionId()
                    + ", \u0438\u0441\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435: minecraft:ladder; buildProtectionEnabled=" + settings.isBuildProtectionEnabled()
                    + ", pvpProtectionEnabled=" + settings.isPvpProtectionEnabled()));
            return;
        }
        if (args.length != 3 || !"world".equals(args[0])
                || !("buildmin".equals(args[1]) || "buildmax".equals(args[1]) || "pvpmax".equals(args[1]))) {
            throw new WrongUsageException(getUsage(sender));
        }
        int height;
        try {
            height = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            throw new CommandException("\u0412\u044b\u0441\u043e\u0442\u0430 Y \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u0446\u0435\u043b\u044b\u043c \u0447\u0438\u0441\u043b\u043e\u043c \u043e\u0442 0 \u0434\u043e 255.");
        }
        if (height < 0 || height > 255) {
            throw new CommandException("\u0412\u044b\u0441\u043e\u0442\u0430 Y \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u043e\u0442 0 \u0434\u043e 255.");
        }
        try {
            if ("buildmin".equals(args[1])) {
                settings.setMinBuildY(height);
            } else if ("buildmax".equals(args[1])) {
                settings.setMaxBuildY(height);
            } else {
                settings.setMaxPvpY(height);
            }
        } catch (IllegalArgumentException exception) {
            throw new CommandException(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new CommandException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 WorldRules. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0436\u0443\u0440\u043d\u0430\u043b \u0441\u0435\u0440\u0432\u0435\u0440\u0430.");
        }
        sender.sendMessage(new TextComponentString("WorldRules: " + args[1] + " = " + height + ". \u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                         String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "world");
        }
        if (args.length == 2 && "world".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "buildmin", "buildmax", "pvpmax", "info");
        }
        return Collections.emptyList();
    }
}
