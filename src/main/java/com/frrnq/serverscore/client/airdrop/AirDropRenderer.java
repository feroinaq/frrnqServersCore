package com.frrnq.serverscore.client.airdrop;

import com.frrnq.serverscore.airdrop.AirDropBlock;
import com.frrnq.serverscore.airdrop.AirDropTile;
import com.frrnq.serverscore.airdrop.AirDrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

public final class AirDropRenderer extends TileEntitySpecialRenderer<AirDropTile> {
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft", "textures/entity/chest/normal.png");
    private static final float[] TURQUOISE = {.36F, .95F, .94F};
    private final ModelChest model = new ModelChest();

    @Override public boolean isGlobalRenderer(AirDropTile tile) { return true; }

    @Override public void render(AirDropTile tile, double x, double y, double z, float partial, int damage, float alpha) {
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (state.getBlock() != AirDrops.BLOCK) { return; }
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            bindTexture(damage >= 0 ? DESTROY_STAGES[damage] : CHEST);
            GlStateManager.enableRescaleNormal();
            GlStateManager.color(1, 1, 1, alpha);
            EnumFacing facing = state.getValue(AirDropBlock.FACING);
            GlStateManager.translate(x + .5D, y + 1, z + .5D);
            GlStateManager.scale(1, -1, -1);
            GlStateManager.rotate(facing.getHorizontalAngle(), 0, 1, 0);
            GlStateManager.translate(-.5D, 0, -.5D);
            model.renderAll();
        } finally {
            GlStateManager.popMatrix();
            if (!rescale) { GlStateManager.disableRescaleNormal(); }
            GlStateManager.bindTexture(texture);
            GL11.glPopAttrib();
            GlStateManager.resetColor();
        }
        if (damage < 0 && state.getValue(AirDropBlock.ACTIVE)) {
            renderBeam(x, y, z, partial, tile.getWorld().getTotalWorldTime());
        }
    }

    private void renderBeam(double x, double y, double z, float partial, long time) {
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM);
        int wrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
        int wrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try {
            GlStateManager.enableTexture2D();
            TileEntityBeaconRenderer.renderBeamSegment(x, y + .875D, z, partial, 1, time, 0, 256, TURQUOISE);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.resetColor();
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
            GlStateManager.bindTexture(texture);
            GlStateManager.depthMask(depthWrite);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (lighting) { GlStateManager.enableLighting(); } else { GlStateManager.disableLighting(); }
            if (cull) { GlStateManager.enableCull(); } else { GlStateManager.disableCull(); }
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (!textured) { GlStateManager.disableTexture2D(); }
        }
    }
}
