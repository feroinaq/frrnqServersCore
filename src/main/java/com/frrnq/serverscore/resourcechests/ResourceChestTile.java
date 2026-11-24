package com.frrnq.serverscore.resourcechests;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.NonNullList;

/** Real inventory and ghost templates are intentionally separate. */
public final class ResourceChestTile extends TileEntityLockable implements net.minecraft.util.ITickable {
    private java.util.UUID locationId, locationToken;
    private long appliedLocationGeneration;
    public float lidAngle, previousLidAngle;
    private int viewers, viewerTicks;
    private NonNullList<ItemStack> inventory = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ItemStack[] samples = new ItemStack[27];
    private final int[] chance = new int[27], minimum = new int[27], maximum = new int[27], revision = new int[27];
    public ResourceChestTile() { java.util.Arrays.fill(samples, ItemStack.EMPTY); }
    void assignLocation(java.util.UUID id, java.util.UUID token, long generation) {
        locationId = id; locationToken = token; appliedLocationGeneration = generation; markDirty();
    }
    boolean matchesLocation(java.util.UUID id, java.util.UUID token) { return id != null && token != null && id.equals(locationId) && token.equals(locationToken); }
    long appliedGeneration() { return appliedLocationGeneration; }
    void setAppliedGeneration(long value) { appliedLocationGeneration = value; markDirty(); }
    void clearLocation() {
        if (locationId != null || locationToken != null) {
            locationId = null; locationToken = null; appliedLocationGeneration = 0; markDirty();
        }
    }
    @Override public void onLoad() { super.onLoad(); ResourceLocations.applyPending(this); }
    public ItemStack sample(int slot) { return samples[slot].copy(); }
    public int chance(int slot) { return chance[slot]; }
    public int minimum(int slot) { return minimum[slot]; }
    public int maximum(int slot) { return maximum[slot]; }
    public int revision(int slot) { return revision[slot]; }
    public void setSample(int slot, ItemStack source) {
        if (world == null || world.isRemote) { return; }
        ItemStack copy = source.copy();
        if (!copy.isEmpty()) { copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize()))); }
        boolean same = !copy.isEmpty() && ItemStack.areItemsEqual(copy, samples[slot]) && ItemStack.areItemStackTagsEqual(copy, samples[slot]);
        samples[slot] = copy;
        if (copy.isEmpty()) { chance[slot] = minimum[slot] = maximum[slot] = 0; }
        else if (!same) { chance[slot] = 100; minimum[slot] = 1; maximum[slot] = copy.getCount(); }
        revision[slot] = (revision[slot] + 1) & 32767;
        markDirty();
    }
    public boolean configure(int slot, int probability, int min, int max, int expectedRevision) {
        if (world == null || world.isRemote || slot < 0 || slot >= 27 || samples[slot].isEmpty()
                || expectedRevision != revision[slot] || probability < 0 || probability > 100
                || min < 1 || max < min || max > Math.min(64, samples[slot].getMaxStackSize())) { return false; }
        chance[slot] = probability; minimum[slot] = min; maximum[slot] = max;
        revision[slot] = (revision[slot] + 1) & 32767;
        markDirty(); return true;
    }
    public void refill() {
        if (world == null || world.isRemote) { return; }
        ResourceLocations.acknowledgeRefill(this);
        // Keep the 27-slot storage, replacing every slot; never touch the templates.
        for (int slot = 0; slot < 27; slot++) { inventory.set(slot, ItemStack.EMPTY); }
        for (int slot = 0; slot < 27; slot++) {
            if (!samples[slot].isEmpty() && world.rand.nextInt(100) < chance[slot]) {
                ItemStack generated = samples[slot].copy();
                generated.setCount(minimum[slot] + world.rand.nextInt(maximum[slot] - minimum[slot] + 1));
                inventory.set(slot, generated);
            }
        }
        markDirty();
        // Send a complete authoritative view now, including empty slots and the real cursor.
        // Other players may be viewing the chest when an administrator refills it.
        for (EntityPlayer viewer : world.playerEntities) {
            if (viewer instanceof net.minecraft.entity.player.EntityPlayerMP && viewer.openContainer instanceof ContainerChest
                    && ((ContainerChest) viewer.openContainer).getLowerChestInventory() == this) {
                viewer.openContainer.detectAndSendChanges();
                ((net.minecraft.entity.player.EntityPlayerMP) viewer).sendContainerToPlayer(viewer.openContainer);
            }
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        ItemStackHelper.saveAllItems(nbt, inventory);
        if (locationId != null && locationToken != null) {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setUniqueId("Location", locationId); binding.setUniqueId("Token", locationToken);
            binding.setLong("Generation", appliedLocationGeneration); nbt.setTag("ResourceBinding", binding);
        } else { nbt.removeTag("ResourceBinding"); }
        NBTTagList entries = new NBTTagList();
        for (int slot = 0; slot < 27; slot++) {
            if (samples[slot].isEmpty()) { continue; }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", slot); entry.setTag("Sample", samples[slot].writeToNBT(new NBTTagCompound()));
            entry.setInteger("Chance", chance[slot]); entry.setInteger("Min", minimum[slot]); entry.setInteger("Max", maximum[slot]);
            entries.appendTag(entry);
        }
        nbt.setTag("ResourceTemplates", entries); return nbt;
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagCompound binding = nbt.getCompoundTag("ResourceBinding");
        locationId = binding.hasUniqueId("Location") ? binding.getUniqueId("Location") : null;
        locationToken = binding.hasUniqueId("Token") ? binding.getUniqueId("Token") : null;
        appliedLocationGeneration = Math.max(0, binding.getLong("Generation"));
        inventory = NonNullList.withSize(27, ItemStack.EMPTY); ItemStackHelper.loadAllItems(nbt, inventory);
        java.util.Arrays.fill(samples, ItemStack.EMPTY);
        java.util.Arrays.fill(chance, 0); java.util.Arrays.fill(minimum, 0); java.util.Arrays.fill(maximum, 0);
        NBTTagList entries = nbt.getTagList("ResourceTemplates", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i); int slot = entry.getInteger("Slot");
            if (slot < 0 || slot >= 27) { continue; }
            ItemStack stack = new ItemStack(entry.getCompoundTag("Sample"));
            if (stack.isEmpty()) { continue; }
            int cap = Math.max(1, Math.min(64, stack.getMaxStackSize())); stack.setCount(Math.min(cap, stack.getCount()));
            samples[slot] = stack;
            chance[slot] = Math.max(0, Math.min(100, entry.getInteger("Chance")));
            minimum[slot] = Math.max(1, Math.min(cap, entry.getInteger("Min")));
            maximum[slot] = Math.max(minimum[slot], Math.min(cap, entry.getInteger("Max")));
        }
    }
    @Override public boolean shouldRefresh(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
        // Rotation must not recreate this mod TileEntity and erase its saved templates.
        return oldState.getBlock() != newState.getBlock();
    }
    @Override public int getSizeInventory() { return 27; }
    @Override public boolean isEmpty() { ResourceLocations.applyPending(this); for (ItemStack stack : inventory) { if (!stack.isEmpty()) { return false; } } return true; }
    @Override public ItemStack getStackInSlot(int index) { ResourceLocations.applyPending(this); return inventory.get(index); }
    @Override public ItemStack decrStackSize(int index, int count) { ResourceLocations.applyPending(this); ItemStack result = ItemStackHelper.getAndSplit(inventory, index, count); if (!result.isEmpty()) { markDirty(); } return result; }
    @Override public ItemStack removeStackFromSlot(int index) { ResourceLocations.applyPending(this); ItemStack result = ItemStackHelper.getAndRemove(inventory, index); markDirty(); return result; }
    @Override public void setInventorySlotContents(int index, ItemStack stack) {
        ResourceLocations.applyPending(this); inventory.set(index, stack); if (!stack.isEmpty()) { stack.setCount(Math.min(stack.getCount(), Math.min(getInventoryStackLimit(), stack.getMaxStackSize()))); } markDirty();
    }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUsableByPlayer(EntityPlayer player) { return world != null && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this && player.getDistanceSq(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64; }
    @Override public String getName() { return "container.frrnqserverscore.resource_chest"; }
    @Override public boolean hasCustomName() { return false; }
    private void syncViewers() { world.addBlockEvent(pos, getBlockType(), 1, viewers); }
    @Override public void openInventory(EntityPlayer player) {
        ResourceLocations.applyPending(this);
        if (world != null && !world.isRemote && !player.isSpectator()) { viewers++; syncViewers(); }
    }
    @Override public void closeInventory(EntityPlayer player) {
        if (world != null && !world.isRemote && !player.isSpectator()) { viewers = Math.max(0, viewers - 1); syncViewers(); }
    }
    @Override public boolean receiveClientEvent(int id, int value) {
        if (id == 1) { viewers = Math.max(0, value); return true; }
        return super.receiveClientEvent(id, value);
    }
    @Override public void update() {
        if (!world.isRemote && ++viewerTicks % 20 == 0) {
            int count = 0;
            for (EntityPlayer player : world.playerEntities) {
                if (player.isSpectator()) { continue; }
                Container open = player.openContainer;
                if ((open instanceof ContainerChest && ((ContainerChest) open).getLowerChestInventory() == this)
                        || (open instanceof ResourceEditorContainer && ((ResourceEditorContainer) open).tile == this)) { count++; }
            }
            if (count != viewers || count > 0) { viewers = count; syncViewers(); }
        }
        previousLidAngle = lidAngle;
        lidAngle = Math.max(0, Math.min(1, lidAngle + (viewers > 0 ? .1F : -.1F)));
    }
    @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount() { return 0; }
    @Override public void clear() { ResourceLocations.applyPending(this); inventory.clear(); markDirty(); }
    @Override public String getGuiID() { return "minecraft:chest"; }
    @Override public Container createContainer(InventoryPlayer inventory, EntityPlayer player) { ResourceLocations.applyPending(this); return new ContainerChest(inventory, this, player); }
}