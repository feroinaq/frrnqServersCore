package com.frrnq.serverscore.client.resource;

import com.frrnq.serverscore.resourcechests.ResourceChestBlock;
import com.frrnq.serverscore.resourcechests.ResourceChestTile;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/** Vanilla single-chest model/texture, without merging neighboring resource inventories. */
public final class ResourceChestRenderer extends TileEntitySpecialRenderer<ResourceChestTile> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/chest/normal.png");
    private final ModelChest model = new ModelChest();
    @Override public void render(ResourceChestTile tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        EnumFacing facing = EnumFacing.SOUTH;
        float lid = 0;
        if (tile != null && tile.hasWorld()) {
            facing = tile.getWorld().getBlockState(tile.getPos()).getValue(ResourceChestBlock.FACING);
            lid = tile.previousLidAngle + (tile.lidAngle - tile.previousLidAngle) * partialTicks;
        }
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        bindTexture(destroyStage >= 0 ? DESTROY_STAGES[destroyStage] : TEXTURE);
        if (destroyStage >= 0) {
            GlStateManager.matrixMode(5890); GlStateManager.pushMatrix();
            GlStateManager.scale(4, 4, 1); GlStateManager.translate(.0625F, .0625F, .0625F);
            GlStateManager.matrixMode(5888);
        }
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        try {
            GlStateManager.color(1, 1, 1, alpha);
            GlStateManager.translate(x, y + 1, z + 1);
            GlStateManager.scale(1, -1, -1);
            GlStateManager.translate(.5F, .5F, .5F);
            float rotation = facing == EnumFacing.NORTH ? 180 : facing == EnumFacing.WEST ? 90 : facing == EnumFacing.EAST ? -90 : 0;
            GlStateManager.rotate(rotation, 0, 1, 0); GlStateManager.translate(-.5F, -.5F, -.5F);
            float inverse = 1 - lid;
            model.chestLid.rotateAngleX = -(1 - inverse * inverse * inverse) * ((float) Math.PI / 2);
            model.renderAll();
        } finally {
            GlStateManager.disableRescaleNormal(); GlStateManager.popMatrix();
            if (destroyStage >= 0) {
                GlStateManager.matrixMode(5890); GlStateManager.popMatrix(); GlStateManager.matrixMode(5888);
            }
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}