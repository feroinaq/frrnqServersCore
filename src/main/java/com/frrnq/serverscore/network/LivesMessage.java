package com.frrnq.serverscore.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import com.frrnq.serverscore.FrrnqServersCore;

public final class LivesMessage implements IMessage {
    public int lives;
    public int maximum;
    public LivesMessage() {}
    public LivesMessage(int lives, int maximum) { this.lives = lives; this.maximum = maximum; }
    @Override public void fromBytes(ByteBuf buffer) { lives = buffer.readInt(); maximum = buffer.readInt(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(lives); buffer.writeInt(maximum); }

    public static final class Handler implements IMessageHandler<LivesMessage, IMessage> {
        @Override public IMessage onMessage(LivesMessage message, MessageContext context) {
            FrrnqServersCore.proxy.receive(message, context);
            return null;
        }
    }
}