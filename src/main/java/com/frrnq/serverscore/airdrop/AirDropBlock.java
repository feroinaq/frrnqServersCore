package com.frrnq.serverscore.airdrop;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class AirDropBlock extends BlockContainer {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    private static final AxisAlignedBB BOX = new AxisAlignedBB(.0625, 0, .0625, .9375, .875, .9375);
    public AirDropBlock() {
        super(Material.WOOD); setSoundType(net.minecraft.block.SoundType.WOOD); setRegistryName("frrnqserverscore", "airdrop_chest");
        setUnlocalizedName("frrnqserverscore.airdrop_chest"); setHardness(2.5F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(ACTIVE, false));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, ACTIVE); }
    @Override public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.getFront(meta & 7);
        if (facing.getAxis() == EnumFacing.Axis.Y) { facing = EnumFacing.NORTH; }
        return getDefaultState().withProperty(FACING, facing).withProperty(ACTIVE, (meta & 8) != 0);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex() | (state.getValue(ACTIVE) ? 8 : 0); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) { return withRotation(state, mirror.toRotation(state.getValue(FACING))); }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return BOX; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing side) { return BlockFaceShape.UNDEFINED; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new AirDropTile(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public int quantityDropped(java.util.Random random) { return 0; }
    @Override protected boolean canSilkHarvest() { return false; }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult hit, World world, BlockPos pos, EntityPlayer player) {
        return ItemStack.EMPTY;
    }
    @Override public EnumPushReaction getMobilityFlag(IBlockState state) { return EnumPushReaction.BLOCK; }

    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean harvest) {
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || !player.isCreative() || !(tile instanceof AirDropTile)) {
            return super.removedByPlayer(state, world, pos, player, harvest);
        }
        AirDropTile chest = (AirDropTile) tile;
        chest.creativeRemoval = true;
        try {
            return super.removedByPlayer(state, world, pos, player, harvest);
        } finally {
            chest.creativeRemoval = false;
        }
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !world.restoringBlockSnapshots) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof AirDropTile && !((AirDropTile) tile).creativeRemoval) {
                ((AirDropTile) tile).dropContents();
            }
        }
        super.breakBlock(world, pos, state);
    }
}
