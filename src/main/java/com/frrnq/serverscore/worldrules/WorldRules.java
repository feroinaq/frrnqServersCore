package com.frrnq.serverscore.worldrules;

import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/** Owns the shared configuration and registers the module once. */
public final class WorldRules {
    private final WorldRulesConfig settings;

    public WorldRules(File configDirectory) {
        settings = new WorldRulesConfig(new File(configDirectory, "frrnqserverscore-worldrules.cfg"));
        MinecraftForge.EVENT_BUS.register(new BuildProtection(settings));
        MinecraftForge.EVENT_BUS.register(new PvPProtection(settings));
    }

    public void serverStarting(FMLServerStartingEvent event) {
        // Reload for each server, including successive integrated servers in one client.
        settings.load();
        event.registerServerCommand(new com.frrnq.serverscore.command.FcoreCommand(new WorldRulesCommand(settings)));
    }
}