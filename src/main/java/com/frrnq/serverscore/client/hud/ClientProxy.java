package com.frrnq.serverscore.client.hud;

import com.frrnq.serverscore.network.CommonProxy;
import com.frrnq.serverscore.network.LivesMessage;
import com.frrnq.serverscore.network.VideoControlMessage;
import com.frrnq.serverscore.video.VideoPlaybackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ClientProxy extends CommonProxy {
    private VideoPlaybackManager video;
    private final TeamStatusHud teams = new TeamStatusHud();
    private final LivesHud hud = new LivesHud();
    private final com.frrnq.serverscore.client.resource.ResourceNoticeHud notices = new com.frrnq.serverscore.client.resource.ResourceNoticeHud();
    @Override public void init() {
        MinecraftForge.EVENT_BUS.register(hud);
        MinecraftForge.EVENT_BUS.register(teams);
        MinecraftForge.EVENT_BUS.register(notices);
        MinecraftForge.EVENT_BUS.register(new BorderWarningHud());
        video = VideoPlaybackManager.init();
    }
    @Override public Object resourceEditor(net.minecraft.entity.player.EntityPlayer player, com.frrnq.serverscore.resourcechests.ResourceChestTile tile) {
        return new com.frrnq.serverscore.client.resource.ResourceEditorGui(player, tile);
    }
    @Override public void resourceNotice(com.frrnq.serverscore.network.ResourceNoticeMessage message, MessageContext context) {
        NetworkManager source = context.getClientHandler().getNetworkManager();
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> {
            if (minecraft.getConnection() != null && minecraft.getConnection().getNetworkManager() == source) {
                notices.show(source, message.location, message.type, message.remainingMillis);
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
    @Override public void videoControl(VideoControlMessage message, MessageContext context) {
        long received = System.nanoTime();
        NetworkManager source = context.getClientHandler().getNetworkManager();
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager() == source) {
                video.receive(source, message, received);
            }
        });
    }
    @Override public void teamStatus(com.frrnq.serverscore.network.TeamStatusMessage message, MessageContext context) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkManager source = context.getClientHandler().getNetworkManager();
        mc.addScheduledTask(() -> {
            if (mc.getConnection() != null && mc.getConnection().getNetworkManager() == source) {
                teams.update(source, message);
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
