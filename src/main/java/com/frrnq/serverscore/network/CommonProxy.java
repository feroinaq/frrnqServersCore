package com.frrnq.serverscore.network;

import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CommonProxy {
    public void openAirDropEditor(AirDropEditorMessage message, MessageContext context) {}
    public void init() {}
    public void resourceNotice(ResourceNoticeMessage message, MessageContext context) {}
    public Object resourceEditor(net.minecraft.entity.player.EntityPlayer player, com.frrnq.serverscore.resourcechests.ResourceChestTile tile) { return null; }
    public void receive(LivesMessage message, MessageContext context) {}
}
