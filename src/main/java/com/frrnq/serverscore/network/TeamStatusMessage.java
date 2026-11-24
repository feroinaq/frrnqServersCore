package com.frrnq.serverscore.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.frrnq.serverscore.FrrnqServersCore;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Ordered server snapshot. No player identities and no client-to-server actions. */
public final class TeamStatusMessage implements IMessage {
    public static final class Row {
        public final String name;
        public final int color;
        public final int count;
        public Row(String name, int color, int count) {
            this.name = name; this.color = color; this.count = count;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Row)) { return false; }
            Row row = (Row) other;
            return name.equals(row.name) && color == row.color && count == row.count;
        }
        @Override public int hashCode() { return java.util.Objects.hash(name, color, count); }
    }
    public boolean active;
    public List<Row> rows = Collections.emptyList();
    public TeamStatusMessage() {}
    public TeamStatusMessage(boolean active, List<Row> rows) {
        this.active = active;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }
    public boolean sameAs(TeamStatusMessage other) {
        return other != null && active == other.active && rows.equals(other.rows);
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeInt(rows.size());
        for (Row row : rows) {
            ByteBufUtils.writeUTF8String(buffer, row.name);
            buffer.writeInt(row.color); buffer.writeInt(row.count);
        }
    }
    @Override public void fromBytes(ByteBuf buffer) {
        active = buffer.readBoolean();
        int size = buffer.readInt();
        if (size < 0 || size > buffer.readableBytes() / 9) { throw new IllegalArgumentException("Invalid team count"); }
        List<Row> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String name = ByteBufUtils.readUTF8String(buffer);
            int color = buffer.readInt(), count = buffer.readInt();
            if (!name.matches("[a-z0-9_-]{1,32}") || count <= 0) { throw new IllegalArgumentException("Invalid team row"); }
            decoded.add(new Row(name, color & 0xFFFFFF, count));
        }
        rows = Collections.unmodifiableList(decoded);
    }
    public static final class Handler implements IMessageHandler<TeamStatusMessage, IMessage> {
        @Override public IMessage onMessage(TeamStatusMessage message, MessageContext context) {
            FrrnqServersCore.proxy.teamStatus(message, context);
            return null;
        }
    }
}
