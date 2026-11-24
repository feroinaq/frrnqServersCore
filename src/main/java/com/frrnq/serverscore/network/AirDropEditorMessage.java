package com.frrnq.serverscore.network;

import com.frrnq.serverscore.FrrnqServersCore;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Opens a server-owned container. Subsequent clicks use vanilla window transactions. */
public final class AirDropEditorMessage implements IMessage {
    public int windowId;
    public String name;

    public AirDropEditorMessage() {}
    public AirDropEditorMessage(int windowId, String name) { this.windowId = windowId; this.name = name; }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(windowId);
        ByteBufUtils.writeUTF8String(buf, name);
    }
    @Override public void fromBytes(ByteBuf buf) {
        windowId = buf.readUnsignedByte();
        name = ByteBufUtils.readUTF8String(buf);
    }
    public static final class Handler implements IMessageHandler<AirDropEditorMessage, IMessage> {
        @Override public IMessage onMessage(AirDropEditorMessage message, MessageContext context) {
            FrrnqServersCore.proxy.openAirDropEditor(message, context);
            return null;
        }
    }
}
