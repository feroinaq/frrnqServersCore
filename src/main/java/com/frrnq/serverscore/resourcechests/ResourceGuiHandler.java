package com.frrnq.serverscore.resourcechests;

import com.frrnq.serverscore.FrrnqServersCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class ResourceGuiHandler implements IGuiHandler {
    public static ResourceChestTile find(World world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= world.getHeight() || !world.isBlockLoaded(pos)
                || world.getBlockState(pos).getBlock() != ResourceChests.BLOCK) { return null; }
        return world.getTileEntity(pos) instanceof ResourceChestTile ? (ResourceChestTile) world.getTileEntity(pos) : null;
    }
    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        ResourceChestTile tile = find(world, new BlockPos(x, y, z));
        return id == 0 && tile != null && player.canUseCommand(2, "fcore") && tile.isUsableByPlayer(player)
                ? new ResourceEditorContainer(player.inventory, tile) : null;
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        ResourceChestTile tile = find(world, new BlockPos(x, y, z));
        return id == 0 && tile != null ? FrrnqServersCore.proxy.resourceEditor(player, tile) : null;
    }
}