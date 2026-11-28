package com.frrnq.serverscore.video;

import java.nio.FloatBuffer;
import com.frrnq.serverscore.network.VideoControlMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

final class VideoPreparationHud extends Gui {
    private final RemoteVideoClient remote;
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);

    VideoPreparationHud(RemoteVideoClient remote) { this.remote = remote; }

    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.world == null || mc.player == null
                || mc.getConnection() == null || mc.gameSettings.hideGUI) { return; }
        VideoControlMessage stats = remote.preparation();
        if (stats == null) { return; }
        int width = event.getResolution().getScaledWidth();
        int height = event.getResolution().getScaledHeight();
        float scale = Math.min(1, Math.min((width - 16) / 470.0F, (height - 36) / 77.0F));
        if (scale <= 0) { return; }

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        color.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(width - 8 - 232 * scale, height - 30 - 77 * scale, 0);
            GlStateManager.scale(scale, scale, 1);
            drawRect(0, 0, 232, 77, 0xE010141C);
            drawRect(0, 0, 2, 77, 0xFFE04B55);
            mc.fontRenderer.drawString("FRRNQ SERVERS", 9, 5, 0xFFB8BDC8);
            mc.fontRenderer.drawString("\u041f\u041e\u0414\u0413\u041e\u0422\u041e\u0412\u041a\u0410 \u0422\u0420\u0410\u041d\u0421\u041b\u042f\u0426\u0418\u0418", 9, 16, 0xFFF5F0F5);
            mc.fontRenderer.drawString("\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435: " + phase(stats.phase), 9, 28, 0xFFB8BDC8);
            mc.fontRenderer.drawString("\u0413\u043e\u0442\u043e\u0432\u044b: " + stats.ready + "/" + stats.total, 9, 40, 0xFFF5F0F5);
            mc.fontRenderer.drawString("\u0413\u043e\u0442\u043e\u0432\u044f\u0442\u0441\u044f: " + (stats.total - stats.ready - stats.errors)
                    + "   \u041e\u0448\u0438\u0431\u043a\u0438: " + stats.errors, 9, 52, 0xFFF5F0F5);
            drawRect(9, 66, 223, 71, 0xFF303744);
            int filled = stats.total == 0 ? 0 : (int) (214L * stats.ready / stats.total);
            if (filled > 0) { drawRect(9, 66, 9 + filled, 71, 0xFFE04B55); }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (textured) { GlStateManager.enableTexture2D(); } else { GlStateManager.disableTexture2D(); }
            if (alpha) { GlStateManager.enableAlpha(); } else { GlStateManager.disableAlpha(); }
            GlStateManager.bindTexture(bound);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }

    private static String phase(VideoControlMessage.Preparation phase) {
        switch (phase) {
            case DOWNLOADING: return "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430";
            case BUFFERING: return "\u0411\u0443\u0444\u0435\u0440\u0438\u0437\u0430\u0446\u0438\u044f";
            case READY: return "\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435 \u0437\u0430\u043f\u0443\u0441\u043a\u0430";
            case SCHEDULED: return "\u0421\u0442\u0430\u0440\u0442 \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d";
            case ERROR: return "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043a\u0438";
            default: return "";
        }
    }
}
