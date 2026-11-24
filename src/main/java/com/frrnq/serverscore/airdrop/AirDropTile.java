package com.frrnq.serverscore.airdrop;

import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class AirDropTile extends TileEntity {
    // Private storage: no inventory interface or item-handler capability for pipes and hoppers.
    private final NonNullList<ItemStack> loot = NonNullList.withSize(AirDropData.SLOTS, ItemStack.EMPTY);

    boolean creativeRemoval;

    void fill(AirDropData.Template template) {
        for (int i = 0; i < loot.size(); i++) { loot.set(i, template.copySlot(i)); }
        markDirty();
    }

    void dropContents() {
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.set(i, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        loot.clear();
        ItemStackHelper.loadAllItems(tag, loot);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        return ItemStackHelper.saveAllItems(super.writeToNBT(tag), loot);
    }

    @Override public boolean onlyOpsCanSetNbt() { return true; }
    @Override public boolean restrictNBTCopy() { return true; }
    @Override public boolean shouldRefresh(World world, BlockPos pos, IBlockState before, IBlockState after) {
        return before.getBlock() != after.getBlock();
    }

    @Override public double getMaxRenderDistanceSquared() { return 65536.0D; }
    @Override public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 257, pos.getZ() + 1);
    }
}
