package com.frrnq.serverscore;

import com.frrnq.serverscore.worldrules.WorldRules;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = FrrnqServersCore.MODID, name = FrrnqServersCore.NAME, version = FrrnqServersCore.VERSION)
public class FrrnqServersCore {
    public static final String MODID = "frrnqserverscore";
    public static final String NAME = "frrnqServersCore";
    public static final String VERSION = "1.0";

    @net.minecraftforge.fml.common.SidedProxy(clientSide = "com.frrnq.serverscore.client.hud.ClientProxy", serverSide = "com.frrnq.serverscore.network.CommonProxy")
    public static com.frrnq.serverscore.network.CommonProxy proxy;

    @Mod.Instance(MODID)
    public static FrrnqServersCore instance;

    private static Logger logger;
    private WorldRules worldRules;

    @EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        com.frrnq.serverscore.airdrop.AirDrops.stopped();
        com.frrnq.serverscore.network.DeathSync.clear();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        worldRules.serverStarting(event);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        com.frrnq.serverscore.resourcechests.ResourceChests.init();
        com.frrnq.serverscore.airdrop.AirDrops.init();
        com.frrnq.serverscore.network.CoreNetwork.init();
        proxy.init();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.frrnq.serverscore.network.DeathSync());
        new com.frrnq.serverscore.death.DeathLimit();
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(new com.frrnq.serverscore.teamspawn.PendingTeamHandler());
        worldRules = new WorldRules(event.getModConfigurationDirectory());
        logger = event.getModLog();
        logger.info("{} is initializing", NAME);
    }
}
