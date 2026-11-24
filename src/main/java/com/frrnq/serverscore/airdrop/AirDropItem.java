package com.frrnq.serverscore.airdrop;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

final class AirDropItem extends ItemBlock {
    AirDropItem(AirDropBlock block) {
        super(block);
        setRegistryName(block.getRegistryName());
        setMaxStackSize(1);
    }

    static ItemStack prepared(AirDropData.Template template) {
        ItemStack stack = new ItemStack(AirDrops.BLOCK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Template", template.name);
        tag.setUniqueId("TemplateId", template.id);
        stack.setTagCompound(tag);
        stack.setStackDisplayName("AirDrop \u00b7 " + template.name);
        return stack;
    }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float x, float y, float z) {
        if (!world.isBlockLoaded(pos)) { return EnumActionResult.FAIL; }
        BlockPos target = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        if (!validTarget(world, target)) { return EnumActionResult.FAIL; }
        return super.onItemUse(player, world, pos, hand, side, x, y, z);
    }

    private static boolean validTarget(World world, BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < world.getHeight()
                && world.getWorldBorder().contains(pos) && world.isBlockLoaded(pos);
    }

    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
            EnumFacing side, float x, float y, float z, IBlockState state) {
        if (!validTarget(world, pos) || state.getBlock() != block) { return false; }
        if (world.isRemote) { return false; } // The authoritative placement arrives with its beam state.
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer || !player.canUseCommand(2, "fcore")) {
            if (player != null) { player.sendStatusMessage(new TextComponentString("\u0414\u043b\u044f \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0438 AirDrop \u043d\u0443\u0436\u0435\u043d OP 2."), true); }
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        AirDropData.Template template = tag == null ? null
                : AirDropData.get(world.getMinecraftServer()).find(tag.getString("Template"));
        if (template == null || !tag.hasUniqueId("TemplateId") || !template.id.equals(tag.getUniqueId("TemplateId"))
                || template.filled() == 0) {
            player.sendStatusMessage(new TextComponentString("\u0428\u0430\u0431\u043b\u043e\u043d AirDrop \u043f\u0443\u0441\u0442 \u0438\u043b\u0438 \u0443\u0434\u0430\u043b\u0451\u043d."), true);
            return false;
        }
        // Do not merge BlockEntityTag from the held item. Only this server's template supplies loot.
        if (!world.setBlockState(pos, state.withProperty(AirDropBlock.ACTIVE, true), 11)) { return false; }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof AirDropTile) {
            ((AirDropTile) tile).fill(template);
            return true;
        }
        return false;
    }
}
