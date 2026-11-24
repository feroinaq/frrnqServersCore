package com.frrnq.serverscore.client.hud;

import com.frrnq.serverscore.network.CommonProxy;
import com.frrnq.serverscore.network.LivesMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ClientProxy extends CommonProxy {
    private final LivesHud hud = new LivesHud();
    private final com.frrnq.serverscore.client.resource.ResourceNoticeHud notices = new com.frrnq.serverscore.client.resource.ResourceNoticeHud();
    @Override public void init() { MinecraftForge.EVENT_BUS.register(hud); MinecraftForge.EVENT_BUS.register(notices); }
    @Override public Object resourceEditor(net.minecraft.entity.player.EntityPlayer player, com.frrnq.serverscore.resourcechests.ResourceChestTile tile) {
        return new com.frrnq.serverscore.client.resource.ResourceEditorGui(player, tile);
    }
    @Override public void resourceNotice(com.frrnq.serverscore.network.ResourceNoticeMessage message, MessageContext context) {
        NetworkManager source = context.getClientHandler().getNetworkManager();
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> {
            if (minecraft.getConnection() != null && minecraft.getConnection().getNetworkManager() == source) {
                notices.show(source, message.location, message.type);
            }
        });
    }
    @Override public void openAirDropEditor(com.frrnq.serverscore.network.AirDropEditorMessage message, MessageContext context) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkManager source = context.getClientHandler().getNetworkManager();
        mc.addScheduledTask(() -> {
            if (mc.player != null && mc.getConnection() != null && mc.getConnection().getNetworkManager() == source) {
                mc.displayGuiScreen(new com.frrnq.serverscore.client.airdrop.AirDropEditorGui(
                        mc.player.inventory, message.name, message.windowId));
            }
        });
    }
    @Override public void receive(LivesMessage message, MessageContext context) {
        NetworkManager source = context.getClientHandler().getNetworkManager();
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> {
            if (minecraft.getConnection() != null && minecraft.getConnection().getNetworkManager() == source) {
                hud.update(source, message.lives, message.maximum);
            }
        });
    }
}
