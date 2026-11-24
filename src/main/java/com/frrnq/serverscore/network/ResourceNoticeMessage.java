package com.frrnq.serverscore.network;

import com.frrnq.serverscore.FrrnqServersCore;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server event only; it contains no client-to-server actions. */
public final class ResourceNoticeMessage implements IMessage {
    public String location;
    public enum Type { UPDATED, WARNING, AIRDROP, GAME_START, BORDER_START, BORDER_END }
    public Type type;
    public long remainingMillis;
    public ResourceNoticeMessage() {}
    public ResourceNoticeMessage(String location, boolean warning) { this.location = location; this.type = warning ? Type.WARNING : Type.UPDATED; }
    public static ResourceNoticeMessage airdrop() {
        ResourceNoticeMessage message = new ResourceNoticeMessage();
        message.location = "";
        message.type = Type.AIRDROP;
        return message;
    }
    public static ResourceNoticeMessage gameStart() {
        ResourceNoticeMessage message = new ResourceNoticeMessage();
        message.location = "";
        message.type = Type.GAME_START;
        return message;
    }
    public static ResourceNoticeMessage border(boolean start, long remaining) {
        ResourceNoticeMessage message = new ResourceNoticeMessage();
        message.location = "";
        message.type = start ? Type.BORDER_START : Type.BORDER_END;
        message.remainingMillis = remaining;
        return message;
    }
    @Override public void toBytes(ByteBuf buffer) { ByteBufUtils.writeUTF8String(buffer, location); buffer.writeByte(type.ordinal()); if (type == Type.BORDER_START) { buffer.writeLong(remainingMillis); } }
    @Override public void fromBytes(ByteBuf buffer) { location = ByteBufUtils.readUTF8String(buffer); type = Type.values()[buffer.readUnsignedByte()]; if (type == Type.BORDER_START) { remainingMillis = Math.max(0, buffer.readLong()); } }
    public static final class Handler implements IMessageHandler<ResourceNoticeMessage, IMessage> {
        @Override public IMessage onMessage(ResourceNoticeMessage message, MessageContext context) {
            FrrnqServersCore.proxy.resourceNotice(message, context); return null;
        }
    }
}
