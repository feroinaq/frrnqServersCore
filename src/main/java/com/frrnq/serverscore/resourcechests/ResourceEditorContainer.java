package com.frrnq.serverscore.resourcechests;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ResourceEditorContainer extends Container {
    public final ResourceChestTile tile;
    private final InventoryBasic ghosts = new InventoryBasic("Resource templates", false, 27);
    public final int[] properties = new int[108];
    private final int[] last = new int[108];
    public boolean settingsView;
    public ResourceEditorContainer(InventoryPlayer player, ResourceChestTile tile) {
        this.tile = tile; Arrays.fill(last, -1);
        tile.openInventory(player.player);
        for (int slot = 0; slot < 27; slot++) {
            ghosts.setInventorySlotContents(slot, tile.sample(slot));
            addSlotToContainer(new Slot(ghosts, slot, 9 + slot % 9 * 18, 37 + slot / 9 * 18) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
                @Override public boolean canTakeStack(EntityPlayer player) { return false; }
                @Override public boolean isEnabled() { return !settingsView; }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { addPlayerSlot(player, col + row * 9 + 9, 9 + col * 18, 111 + row * 18); }
        }
        for (int col = 0; col < 9; col++) { addPlayerSlot(player, col, 9 + col * 18, 169); }
    }
    private void addPlayerSlot(InventoryPlayer inventory, int index, int x, int y) {
        addSlotToContainer(new Slot(inventory, index, x, y) { @Override public boolean isEnabled() { return !settingsView; } });
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == tile.getWorld() && player.canUseCommand(2, "fcore") && tile.isUsableByPlayer(player)
                && ResourceGuiHandler.find(player.world, tile.getPos()) == tile;
    }
    @Override public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (!player.world.isRemote && !canInteractWith(player)) { return ItemStack.EMPTY; }
        if (slot >= 0 && slot < 27) {
            if (!player.world.isRemote) {
                if (type == ClickType.QUICK_MOVE && button == 0) { tile.setSample(slot, ItemStack.EMPTY); }
                else if (type == ClickType.PICKUP && button == 0 && !player.inventory.getItemStack().isEmpty()) {
                    tile.setSample(slot, player.inventory.getItemStack());
                }
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, button, type, player);
    }
    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) { return slot.slotNumber >= 27 && super.canMergeSlot(stack, slot); }
    @Override public boolean canDragIntoSlot(Slot slot) { return slot.slotNumber >= 27; }
    @Override public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        tile.closeInventory(player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
    private int property(int id) {
        int slot = id / 4;
        switch (id % 4) {
            case 0: return tile.chance(slot);
            case 1: return tile.minimum(slot);
            case 2: return tile.maximum(slot);
            default: return tile.revision(slot);
        }
    }
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        for (int id = 0; id < 108; id++) { listener.sendWindowProperty(this, id, property(id)); }
    }
    @Override public void detectAndSendChanges() {
        // The client receives ghost stacks through packets; its TileEntity is not authoritative.
        if (tile.getWorld().isRemote) { return; }
        boolean[] changed = new boolean[27];
        for (int slot = 0; slot < 27; slot++) {
            ItemStack stack = tile.sample(slot);
            changed[slot] = !ItemStack.areItemStacksEqual(ghosts.getStackInSlot(slot), stack);
            ghosts.setInventorySlotContents(slot, stack);
        }
        super.detectAndSendChanges();
        // Accepted vanilla click transactions suppress sendSlotContents while
        // isChangingQuantityOnly is true. Ghost slots deliberately have no client
        // prediction, so send their authoritative contents without that suppression.
        for (int slot = 0; slot < 27; slot++) {
            if (!changed[slot]) { continue; }
            for (IContainerListener listener : listeners) {
                if (listener instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) listener).connection.sendPacket(new SPacketSetSlot(windowId, slot, ghosts.getStackInSlot(slot)));
                }
            }
        }
        for (int id = 0; id < 108; id++) {
            int value = property(id);
            if (value != last[id]) {
                for (IContainerListener listener : listeners) { listener.sendWindowProperty(this, id, value); }
                last[id] = value;
            }
        }
    }
    @Override public void updateProgressBar(int id, int value) { if (id >= 0 && id < 108) { properties[id] = value; } }
}