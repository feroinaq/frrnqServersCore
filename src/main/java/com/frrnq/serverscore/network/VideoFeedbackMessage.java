package com.frrnq.serverscore.network;

import com.frrnq.serverscore.video.VideoSessions;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class VideoFeedbackMessage implements IMessage {
    public enum Kind { DOWNLOADED, READY, ERROR, CLOCK }
    public Kind kind;
    public long session;
    public long data;

    public VideoFeedbackMessage() {}
    public VideoFeedbackMessage(Kind kind, long session, long data) {
        this.kind = kind;
        this.session = session;
        this.data = data;
    }

    @Override public void toBytes(ByteBuf buf) { buf.writeByte(kind.ordinal()).writeLong(session).writeLong(data); }
    @Override public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() != 17) { throw new IllegalArgumentException("Invalid video feedback size."); }
        int code = buf.readUnsignedByte();
        session = buf.readLong();
        data = buf.readLong();
        if (session <= 0 || code >= Kind.values().length) { throw new IllegalArgumentException("Invalid video feedback."); }
        kind = Kind.values()[code];
        if (kind == Kind.READY && (data <= 0 || data > VideoControlMessage.MAX_DURATION_US)) {
            throw new IllegalArgumentException("Invalid video duration.");
        }
    }

    public static final class Handler implements IMessageHandler<VideoFeedbackMessage, IMessage> {
        @Override public IMessage onMessage(VideoFeedbackMessage message, MessageContext ctx) {
            VideoSessions.receive(ctx.getServerHandler(), message);
            return null;
        }
    }
}
