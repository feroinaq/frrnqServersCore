package com.frrnq.serverscore.video;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

final class VideoRenderer {
    enum Mode { FULLSCREEN, CORNER, OVERLAY }

    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private final int[][] pixels = new int[1][];
    private int texture;
    private int videoWidth;
    private int videoHeight;

    void clear() {
        if (texture != 0) {
            TextureUtil.deleteTexture(texture);
            texture = 0;
        }
    }

    void render(VideoPlayback playback, Mode mode, int opacity, int width, int height) {
        VideoPlayback.Image frame = playback.frameForDisplay();
        if (frame == null && texture == 0) { return; }
        if (mode == Mode.OVERLAY && opacity == 0) {
            if (frame != null) { playback.recycle(frame); }
            clear();
            return;
        }

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean light = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        color.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);

        // Forge emits Pre(ALL) before setting up the HUD projection. Use a private
        // projection so the next HUD subscriber receives the original GL state.
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, width, height, 0, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            if (frame != null) { upload(frame); }
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            double maxWidth = width, maxHeight = height;
            if (mode == Mode.CORNER) {
                maxWidth = width * .35;
                maxHeight = height * .5;
            } else if (mode == Mode.OVERLAY) {
                maxWidth = width * .75;
                maxHeight = height * .75;
            }
            VideoDecoder.Format format = playback.format;
            // Correct for pixel shape and for rounding when the decoder scales the texture.
            double pictureWidth = format.sourceWidth * format.sampleAspectRatio;
            double pictureHeight = format.sourceHeight;
            double angle = Math.toRadians(format.rotation);
            double cosine = Math.abs(Math.cos(angle)), sine = Math.abs(Math.sin(angle));
            double boundsWidth = pictureWidth * cosine + pictureHeight * sine;
            double boundsHeight = pictureWidth * sine + pictureHeight * cosine;
            double scale = Math.min(maxWidth / boundsWidth, maxHeight / boundsHeight);
            double w = boundsWidth * scale, h = boundsHeight * scale;
            double x = (width - w) / 2, y = (height - h) / 2;
            if (mode == Mode.FULLSCREEN) {
                rectangle(0, 0, width, height, 0, 0, 0, 1);
            } else if (mode == Mode.CORNER) {
                x = width - w - 6;
                y = 6;
                rectangle(x - 2, y - 2, x + w + 2, y + h + 2, .063F, .078F, .11F, .85F);
                rectangle(x - 2, y - 2, x, y + h + 2, .88F, .29F, .33F, 1);
            }

            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(texture);
            GlStateManager.color(1, 1, 1, mode == Mode.OVERLAY ? opacity / 100.0F : 1);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder quad = tessellator.getBuffer();
            GlStateManager.translate(x + w / 2, y + h / 2, 0);
            GlStateManager.rotate((float) -format.rotation, 0, 0, 1);
            double halfWidth = pictureWidth * scale / 2;
            double halfHeight = pictureHeight * scale / 2;
            quad.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            quad.pos(-halfWidth, halfHeight, 0).tex(0, 1).endVertex();
            quad.pos(halfWidth, halfHeight, 0).tex(1, 1).endVertex();
            quad.pos(halfWidth, -halfHeight, 0).tex(1, 0).endVertex();
            quad.pos(-halfWidth, -halfHeight, 0).tex(0, 0).endVertex();
            tessellator.draw();
            if (frame != null) { playback.presented(frame); }
        } finally {
            if (frame != null) { playback.recycle(frame); }
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrixMode);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (depth) { GlStateManager.enableDepth(); }
            if (alpha) { GlStateManager.enableAlpha(); }
            if (light) { GlStateManager.enableLighting(); }
            if (fog) { GlStateManager.enableFog(); }
            if (textured) { GlStateManager.enableTexture2D(); } else { GlStateManager.disableTexture2D(); }
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.setActiveTexture(activeTexture);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }

    private void upload(VideoPlayback.Image frame) {
        if (texture == 0 || frame.width != videoWidth || frame.height != videoHeight) {
            clear();
            videoWidth = frame.width;
            videoHeight = frame.height;
            texture = TextureUtil.glGenTextures();
            TextureUtil.allocateTexture(texture, videoWidth, videoHeight);
        }
        GlStateManager.bindTexture(texture);
        pixels[0] = frame.pixels;
        try {
            // A single mip level with blur=true keeps both filters GL_LINEAR on every upload.
            TextureUtil.uploadTextureMipmap(pixels, videoWidth, videoHeight, 0, 0, true, true);
        } finally {
            pixels[0] = null;
        }
    }

    private static void rectangle(double left, double top, double right, double bottom,
            float red, float green, float blue, float alpha) {
        GlStateManager.disableTexture2D();
        GlStateManager.color(red, green, blue, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder quad = tessellator.getBuffer();
        quad.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        quad.pos(left, bottom, 0).endVertex();
        quad.pos(right, bottom, 0).endVertex();
        quad.pos(right, top, 0).endVertex();
        quad.pos(left, top, 0).endVertex();
        tessellator.draw();
    }
}
