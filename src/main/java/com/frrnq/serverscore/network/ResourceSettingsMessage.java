package com.frrnq.serverscore.network;

import com.frrnq.serverscore.resourcechests.ResourceEditorContainer;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** No position or ItemStack supplied by the client: use the validated open server container. */
public final class ResourceSettingsMessage implements IMessage {
    private int window, slot, chance, min, max, revision;
    public ResourceSettingsMessage() {}
    public ResourceSettingsMessage(int window, int slot, int chance, int min, int max, int revision) {
        this.window = window; this.slot = slot; this.chance = chance; this.min = min; this.max = max; this.revision = revision;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        window = buffer.readInt(); slot = buffer.readInt(); chance = buffer.readInt(); min = buffer.readInt(); max = buffer.readInt(); revision = buffer.readInt();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(window); buffer.writeInt(slot); buffer.writeInt(chance); buffer.writeInt(min); buffer.writeInt(max); buffer.writeInt(revision);
    }
    public static final class Handler implements IMessageHandler<ResourceSettingsMessage, IMessage> {
        @Override public IMessage onMessage(ResourceSettingsMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.isEntityAlive() || !(player.openContainer instanceof ResourceEditorContainer)
                        || player.openContainer.windowId != message.window) { return; }
                ResourceEditorContainer container = (ResourceEditorContainer) player.openContainer;
                if (!container.canInteractWith(player)) { return; }
                boolean saved = container.tile.configure(message.slot, message.chance, message.min, message.max, message.revision);
                player.sendMessage(new TextComponentString(saved ? "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0437\u0430\u043f\u0438\u0441\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b." : "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043e\u0442\u043a\u043b\u043e\u043d\u0435\u043d\u044b: \u0437\u0430\u043f\u0438\u0441\u044c \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0430 \u0434\u0440\u0443\u0433\u0438\u043c \u0440\u0435\u0434\u0430\u043a\u0442\u043e\u0440\u043e\u043c \u0438\u043b\u0438 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f \u043d\u0435\u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b. \u041e\u0442\u043a\u0440\u043e\u0439\u0442\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0437\u0430\u043d\u043e\u0432\u043e."));
                container.detectAndSendChanges();
            });
            return null;
        }
    }
}