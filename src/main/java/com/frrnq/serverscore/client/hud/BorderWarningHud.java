package com.frrnq.serverscore.client.hud;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Stateless: uses the current world's vanilla border every frame, including dimension changes. */
public final class BorderWarningHud extends Gui {
    public static final String TITLE = "\u0412\u042b \u0417\u0410 \u041f\u0420\u0415\u0414\u0415\u041b\u0410\u041c\u0418 \u0411\u0410\u0420\u042c\u0415\u0420\u0410!";
    public static final String HELP = "\u0412\u0435\u0440\u043d\u0438\u0442\u0435\u0441\u044c \u0432 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u0443\u044e \u0437\u043e\u043d\u0443!";
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);

    private static boolean outside(Minecraft mc) {
        if (mc.world == null || mc.player == null || mc.getConnection() == null) { return false; }
        WorldBorder border = mc.world.getWorldBorder();
        return mc.player.posX < border.minX() || mc.player.posX > border.maxX()
                || mc.player.posZ < border.minZ() || mc.player.posZ > border.maxZ();
    }

    public static int reservedHeight(Minecraft mc, int width) {
        return outside(mc) ? (int) Math.ceil(44 * Math.min(1, (width - 8) / 320.0F)) + 6 : 0;
    }

    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.gameSettings.hideGUI || !outside(mc)) { return; }
        int width = event.getResolution().getScaledWidth(), height = event.getResolution().getScaledHeight();
        float livesScale = Math.min(LivesHud.HUD_SCALE, Math.min(width / 160.0F, height / 74.0F));
        float scale = Math.min(1, (width - 8) / 320.0F);
        int top = (int) Math.ceil(74 * livesScale) + 6;
        if (scale <= 0) { return; }
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), test = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        color.clear(); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((width - 320 * scale) / 2, top, 0);
            GlStateManager.scale(scale, scale, 1);
            float pulse = .8F + .2F * (float) Math.sin(System.nanoTime() / 350000000.0);
            drawRect(0, 0, 320, 44, 0xD010141C);
            drawRect(0, 0, 2, 44, (Math.round(255 * pulse) << 24) | 0xE04B55);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            drawCenteredString(mc.fontRenderer, TITLE, 160, 8, 0xFFE96B72);
            drawCenteredString(mc.fontRenderer, HELP, 160, 25, 0xFFF5F0F5);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (texture) { GlStateManager.enableTexture2D(); } else { GlStateManager.disableTexture2D(); }
            if (test) { GlStateManager.enableAlpha(); } else { GlStateManager.disableAlpha(); }
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }
}
