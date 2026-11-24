package com.frrnq.serverscore.client.airdrop;

import java.io.IOException;
import com.frrnq.serverscore.airdrop.AirDropEditorContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;

public final class AirDropEditorGui extends GuiContainer {
    private final String name;
    private boolean ghostClick;

    public AirDropEditorGui(InventoryPlayer inventory, String name, int windowId) {
        super(new AirDropEditorContainer(inventory));
        inventorySlots.windowId = windowId;
        this.name = name;
        xSize = 180;
        ySize = 194;
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        for (int i = 0; i < 27; i++) {
            Slot slot = inventorySlots.getSlot(i);
            if (x >= guiLeft + slot.xPos && x < guiLeft + slot.xPos + 16
                    && y >= guiTop + slot.yPos && y < guiTop + slot.yPos + 16) {
                ghostClick = true;
                if (button == 0 || button == 1) { handleMouseClick(slot, i, button, ClickType.PICKUP); }
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (!ghostClick) { super.mouseClickMove(x, y, button, elapsed); }
    }

    @Override protected void mouseReleased(int x, int y, int button) {
        if (ghostClick) { ghostClick = false; return; }
        super.mouseReleased(x, y, button);
    }

    @Override public void drawScreen(int x, int y, float ticks) {
        drawDefaultBackground();
        super.drawScreen(x, y, ticks);
        renderHoveredToolTip(x, y);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float ticks, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xF010141C);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + ySize, 0xFF55D7D6);
        for (Slot slot : inventorySlots.inventorySlots) {
            int left = guiLeft + slot.xPos;
            int top = guiTop + slot.yPos;
            drawRect(left - 1, top - 1, left + 17, top + 17, 0xFF303744);
            drawRect(left, top, left + 16, top + 16, 0xFF181D27);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRenderer.drawString("FRRNQ SERVERS \u00b7 AIRDROP", 9, 7, 0xFFB8BDC8);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name, 162), 9, 21, 0xFFF5F0F5);
        fontRenderer.drawString("\u041b\u041a\u041c \u2014 \u043a\u043e\u043f\u0438\u044f, \u041f\u041a\u041c \u2014 \u0443\u0431\u0440\u0430\u0442\u044c", 9, 96, 0xFFB8BDC8);
    }
}
