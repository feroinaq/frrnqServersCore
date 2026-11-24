package com.frrnq.serverscore.network;

import com.frrnq.serverscore.FrrnqServersCore;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class CoreNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(FrrnqServersCore.MODID);
    private CoreNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(LivesMessage.Handler.class, LivesMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(ResourceSettingsMessage.Handler.class, ResourceSettingsMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(ResourceNoticeMessage.Handler.class, ResourceNoticeMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(AirDropEditorMessage.Handler.class, AirDropEditorMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(TeamStatusMessage.Handler.class, TeamStatusMessage.class, 4, Side.CLIENT);
    }
}
