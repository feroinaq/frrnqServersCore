package com.frrnq.serverscore.client.resource;

import java.io.IOException;
import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.ResourceSettingsMessage;
import com.frrnq.serverscore.resourcechests.ResourceEditorContainer;
import com.frrnq.serverscore.resourcechests.ResourceChestTile;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Two pages in one server container: opening settings never closes or duplicates inventory. */
public final class ResourceEditorGui extends GuiContainer {
    private final ResourceEditorContainer editor;
    private int selected = -1, chance, minimum, maximum, revision, cap;
    private boolean suppressRelease;
    private ItemStack preview = ItemStack.EMPTY;
    public ResourceEditorGui(EntityPlayer player, ResourceChestTile tile) {
        super(new ResourceEditorContainer(player.inventory, tile));
        editor = (ResourceEditorContainer) inventorySlots; xSize = 180; ySize = 196;
    }
    @Override public void initGui() { super.initGui(); buttons(); }
    private void buttons() {
        buttonList.clear();
        if (selected < 0) { return; }
        for (int row = 0; row < 3; row++) {
            buttonList.add(new GuiButton(row * 2, guiLeft + 10, guiTop + 63 + row * 28, 22, 20, "-"));
            buttonList.add(new GuiButton(row * 2 + 1, guiLeft + 148, guiTop + 63 + row * 28, 22, 20, "+"));
        }
        buttonList.add(new GuiButton(6, guiLeft + 8, guiTop + 165, 80, 20, "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c"));
        buttonList.add(new GuiButton(7, guiLeft + 92, guiTop + 165, 80, 20, "\u041d\u0430\u0437\u0430\u0434"));
    }
    private void openSettings(Slot slot) {
        int id = slot.slotNumber;
        if (!slot.getHasStack() || editor.properties[id * 4 + 2] < 1) { return; }
        selected = id; preview = slot.getStack().copy(); cap = Math.min(64, preview.getMaxStackSize());
        chance = editor.properties[id * 4]; minimum = editor.properties[id * 4 + 1];
        maximum = editor.properties[id * 4 + 2]; revision = editor.properties[id * 4 + 3];
        editor.settingsView = true; buttons();
    }
    @Override protected void handleMouseClick(Slot slot, int slotId, int button, ClickType type) {
        if (selected >= 0) { return; }
        // Settings open only on a physical right click, never a drag/hotbar/clone action.
        if (slot != null && slot.slotNumber < 27
                && (button != 0 || (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE))) { return; }
        super.handleMouseClick(slot, slotId, button, type);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (selected < 0) {
            for (int id = 0; id < 27; id++) {
                Slot slot = editor.getSlot(id);
                if (x >= guiLeft + slot.xPos && x < guiLeft + slot.xPos + 16
                        && y >= guiTop + slot.yPos && y < guiTop + slot.yPos + 16) {
                    suppressRelease = true;
                    if (button == 1) { openSettings(slot); }
                    else if (button == 0) { handleMouseClick(slot, id, button, isShiftKeyDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP); }
                    return;
                }
            }
            super.mouseClicked(x, y, button); return;
        }
        suppressRelease = true;
        if (button == 0) {
            for (GuiButton control : buttonList) {
                if (control.mousePressed(mc, x, y)) { control.playPressSound(mc.getSoundHandler()); actionPerformed(control); break; }
            }
        }
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (suppressRelease) { suppressRelease = false; return; }
        if (selected < 0) { super.mouseReleased(x, y, button); }
    }
    @Override protected void mouseClickMove(int x, int y, int button, long duration) { if (selected < 0 && !suppressRelease) { super.mouseClickMove(x, y, button, duration); } }
    @Override protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: chance = Math.max(0, chance - 1); break;
            case 1: chance = Math.min(100, chance + 1); break;
            case 2: minimum = Math.max(1, minimum - 1); break;
            case 3: minimum = Math.min(maximum, minimum + 1); break;
            case 4: maximum = Math.max(minimum, maximum - 1); break;
            case 5: maximum = Math.min(cap, maximum + 1); break;
            case 6:
                CoreNetwork.CHANNEL.sendToServer(new ResourceSettingsMessage(editor.windowId, selected, chance, minimum, maximum, revision));
                back(); break;
            case 7: back(); break;
            default: break;
        }
    }
    private void back() { selected = -1; editor.settingsView = false; buttons(); }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (selected >= 0) {
            if (key == 1) { back(); }
            else if (mc.gameSettings.keyBindInventory.isActiveAndMatches(key)) { mc.player.closeScreen(); }
            return;
        }
        super.keyTyped(typed, key);
    }
    @Override public void drawScreen(int x, int y, float ticks) {
        drawDefaultBackground(); super.drawScreen(x, y, ticks);
        if (selected < 0) { renderHoveredToolTip(x, y); }
    }
    @Override protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xF010141C);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + ySize, 0xFFE04B55);
        if (selected < 0) {
            for (Slot slot : editor.inventorySlots) {
                drawRect(guiLeft + slot.xPos - 1, guiTop + slot.yPos - 1, guiLeft + slot.xPos + 17, guiTop + slot.yPos + 17, 0xFF303744);
                drawRect(guiLeft + slot.xPos, guiTop + slot.yPos, guiLeft + slot.xPos + 16, guiTop + slot.yPos + 16, 0xFF181D27);
            }
        }
        GlStateManager.color(1, 1, 1, 1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString("FRRNQ SERVERS", 9, 7, 0xFFB8BDC8);
        if (selected < 0) {
            fontRenderer.drawString("\u0428\u0430\u0431\u043b\u043e\u043d \u0440\u0435\u0441\u0443\u0440\u0441\u043d\u043e\u0433\u043e \u0441\u0443\u043d\u0434\u0443\u043a\u0430", 9, 22, 0xFFF5F0F5);
            // Keep help compact, while using full-sized text on the settings page.
            GlStateManager.pushMatrix();
            GlStateManager.scale(.75F, .75F, 1);
            fontRenderer.drawString("\u041b\u041a\u041c: \u043e\u0431\u0440\u0430\u0437\u0435\u0446 \u0441 \u043a\u0443\u0440\u0441\u043e\u0440\u0430; \u041f\u041a\u041c: \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438", 12, 123, 0xFFB8BDC8);
            fontRenderer.drawString("Shift+\u041b\u041a\u041c: \u0443\u0434\u0430\u043b\u0438\u0442\u044c \u0437\u0430\u043f\u0438\u0441\u044c", 12, 135, 0xFFB8BDC8);
            GlStateManager.popMatrix();
        } else {
            fontRenderer.drawString("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0441\u043b\u043e\u0442\u0430 " + (selected + 1), 9, 22, 0xFFF5F0F5);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(preview, 9, 39);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1, 1, 1, 1);
            String name = fontRenderer.trimStringToWidth(preview.getDisplayName(), 137);
            fontRenderer.drawString(name, 30, 43, 0xFFB8BDC8);
            drawCenteredString(fontRenderer, "\u0428\u0430\u043d\u0441: " + chance + "%", 90, 69, 0xFFF5F0F5);
            drawCenteredString(fontRenderer, "\u041c\u0438\u043d\u0438\u043c\u0443\u043c: " + minimum, 90, 97, 0xFFF5F0F5);
            drawCenteredString(fontRenderer, "\u041c\u0430\u043a\u0441\u0438\u043c\u0443\u043c: " + maximum, 90, 125, 0xFFF5F0F5);
            fontRenderer.drawString("\u041b\u0438\u043c\u0438\u0442 \u0441\u0442\u0430\u043a\u0430: " + cap, 10, 148, 0xFFB8BDC8);
        }
    }
}