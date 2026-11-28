package com.frrnq.serverscore.network;

import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.video.VideoDomains;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class VideoControlMessage implements IMessage {
    public enum Kind { STATE, STOP, CLOCK, PREPARATION }
    public enum Preparation { HIDDEN, DOWNLOADING, BUFFERING, READY, SCHEDULED, ERROR }
    public Preparation phase = Preparation.HIDDEN;
    public int total, ready, errors;
    public static final int MAX_DURATION_US = 120000000;

    public Kind kind;
    public long session;
    public long startNanos;
    public long serverNanos;
    public long echo;
    public int durationUs;
    public String url = "";
    public int mode, opacity, volume;

    public VideoControlMessage() {}

    public VideoControlMessage(Kind kind, long session) {
        this.kind = kind;
        this.session = session;
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeByte(kind.ordinal()).writeLong(session);
        if (kind == Kind.CLOCK) { buf.writeLong(echo).writeLong(serverNanos); }
        if (kind == Kind.PREPARATION) { buf.writeByte(phase.ordinal()).writeInt(total).writeInt(ready).writeInt(errors); }
        if (kind == Kind.STATE) {
            buf.writeLong(startNanos).writeInt(durationUs).writeByte(mode).writeByte(opacity).writeByte(volume);
            byte[] address = url.getBytes(StandardCharsets.US_ASCII);
            buf.writeShort(address.length).writeBytes(address);
        }
    }

    @Override public void fromBytes(ByteBuf buf) {
        int code = buf.readUnsignedByte();
        if (code >= Kind.values().length) { throw new IllegalArgumentException("Invalid video control packet."); }
        kind = Kind.values()[code];
        session = buf.readLong();
        if (session <= 0) { throw new IllegalArgumentException("Invalid video session."); }
        if (kind == Kind.CLOCK) { echo = buf.readLong(); serverNanos = buf.readLong(); }
        if (kind == Kind.PREPARATION) {
            if (buf.readableBytes() != 13) { throw new IllegalArgumentException("Invalid video preparation packet size."); }
            int phaseCode = buf.readUnsignedByte();
            total = buf.readInt();
            ready = buf.readInt();
            errors = buf.readInt();
            if (phaseCode >= Preparation.values().length || total < 0 || ready < 0
                    || errors < 0 || ready > total - errors) {
                throw new IllegalArgumentException("Invalid video preparation statistics.");
            }
            phase = Preparation.values()[phaseCode];
        }
        if (kind == Kind.STATE) {
            startNanos = buf.readLong();
            durationUs = buf.readInt();
            mode = buf.readUnsignedByte();
            opacity = buf.readUnsignedByte();
            volume = buf.readUnsignedByte();
            int size = buf.readUnsignedShort();
            if (size > VideoDomains.MAX_URL_BYTES || buf.readableBytes() != size
                    || durationUs < 0 || durationUs > MAX_DURATION_US || mode > 2 || opacity > 100 || volume > 100) {
                throw new IllegalArgumentException("Invalid video state.");
            }
            byte[] address = new byte[size];
            buf.readBytes(address);
            url = new String(address, StandardCharsets.US_ASCII);
        }
        if (buf.isReadable()) { throw new IllegalArgumentException("Unexpected video packet data."); }
    }

    public static final class Handler implements IMessageHandler<VideoControlMessage, IMessage> {
        @Override public IMessage onMessage(VideoControlMessage message, MessageContext ctx) {
            FrrnqServersCore.proxy.videoControl(message, ctx);
            return null;
        }
    }
}
