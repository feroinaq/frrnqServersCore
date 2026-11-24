package com.frrnq.serverscore.airdrop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;

public final class AirDropEditorContainer extends Container {
    private final AirDropData data;
    private final AirDropData.Template template;
    private final InventoryBasic display = new InventoryBasic("AirDrop", false, AirDropData.SLOTS);

    public AirDropEditorContainer(InventoryPlayer inventory) { this(inventory, null, null); }

    AirDropEditorContainer(InventoryPlayer inventory, AirDropData data, AirDropData.Template template) {
        this.data = data;
        this.template = template;
        for (int i = 0; i < AirDropData.SLOTS; i++) {
            if (template != null) { display.setInventorySlotContents(i, template.copySlot(i)); }
            addSlotToContainer(new Slot(display, i, 9 + i % 9 * 18, 36 + i / 9 * 18) {
                @Override public boolean canTakeStack(EntityPlayer player) { return false; }
                @Override public boolean isItemValid(ItemStack stack) { return false; }
            });
        }
        for (int i = 0; i < 27; i++) {
            addSlotToContainer(new Slot(inventory, 9 + i, 9 + i % 9 * 18, 112 + i / 9 * 18));
        }
        for (int i = 0; i < 9; i++) { addSlotToContainer(new Slot(inventory, i, 9 + i * 18, 170)); }
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world.isRemote || (player.isEntityAlive() && player.canUseCommand(2, "fcore")
                && data != null && data.find(template.name) == template);
    }

    @Override public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (!canInteractWith(player) || slot >= inventorySlots.size()) { return ItemStack.EMPTY; }
        if (slot >= 0 && slot < AirDropData.SLOTS) {
            if (!player.world.isRemote && type == ClickType.PICKUP) {
                ItemStack cursor = player.inventory.getItemStack();
                if (button == 0 && !cursor.isEmpty()) { data.setSample(template, slot, cursor); }
                if (button == 1) { data.setSample(template, slot, ItemStack.EMPTY); }
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, button, type, player);
    }

    @Override public boolean canDragIntoSlot(Slot slot) { return slot.slotNumber >= AirDropData.SLOTS; }
    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) { return slot.slotNumber >= AirDropData.SLOTS; }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }

    @Override public void detectAndSendChanges() {
        if (template == null) { return; }
        for (int i = 0; i < AirDropData.SLOTS; i++) {
            ItemStack sample = template.sample(i);
            if (!ItemStack.areItemStacksEqual(display.getStackInSlot(i), sample)) {
                display.setInventorySlotContents(i, sample.copy());
                // Vanilla suppresses ordinary slot updates during a successful click transaction.
                for (IContainerListener listener : listeners) {
                    if (listener instanceof EntityPlayerMP) {
                        ((EntityPlayerMP) listener).connection.sendPacket(new SPacketSetSlot(windowId, i, sample));
                    }
                }
            }
        }
        super.detectAndSendChanges();
    }
}
