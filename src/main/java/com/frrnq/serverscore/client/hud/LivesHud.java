package com.frrnq.serverscore.client.hud;

import java.util.Locale;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** All mutable state is accessed exclusively on the Minecraft thread. */
public final class LivesHud extends Gui {
    public static final float HUD_SCALE = 0.57F;
    private final FloatBuffer previousColor = BufferUtils.createFloatBuffer(16);
    private NetworkManager connection;
    private int lives;
    private int maximum;
    private boolean ready;
    private long lossTime;

    public void update(NetworkManager source, int value, int limit) {
        if (limit < 1 || limit > 100 || value < 0 || value > limit) { return; }
        if (connection != source) { clear(); }
        if (ready && value < lives) { lossTime = System.nanoTime(); }
        else if (ready && value > lives) { lossTime = 0; }
        connection = source;
        lives = value;
        maximum = limit;
        ready = true;
    }
    private void clear() { connection = null; ready = false; lossTime = 0; }
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        NetworkManager old = event.getManager();
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (connection == old) { clear(); }
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection) { clear(); }
    }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !ready
                || mc.player == null || mc.world == null || mc.gameSettings.hideGUI
                || mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection) { return; }
        float scale = Math.min(HUD_SCALE, Math.min(event.getResolution().getScaledWidth() / 160.0F,
                event.getResolution().getScaledHeight() / 74.0F));
        if (scale <= 0) { return; }
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        previousColor.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, previousColor);
        GlStateManager.pushMatrix();
        try {
            // Scale the complete design, including the outer margin, in GUI coordinates.
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(4, 4, 0);
            drawRect(0, 0, 152, 66, 0xB010141C);
            drawRect(0, 0, 2, 66, 0xFFE04B55);
            mc.fontRenderer.drawString("FRRNQ SERVERS", 10, 7, 0xFFB8BDC8);
            String[] heart = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};
            for (int row = 0; row < heart.length; row++) {
                for (int col = 0; col < heart[row].length(); col++) {
                    if (heart[row].charAt(col) == '1') {
                        drawRect(10 + col * 2, 28 + row * 2, 12 + col * 2, 30 + row * 2, 0xFFE54D59);
                    }
                }
            }
            float flash = lossTime == 0 ? 0 : Math.max(0, 1 - (System.nanoTime() - lossTime) / 500000000.0F);
            int green = (int) (240 + (70 - 240) * flash);
            int blue = (int) (245 + (80 - 245) * flash);
            int color = 0xFF000000 | (245 << 16) | (green << 8) | blue;
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(32, 25, 0);
                GlStateManager.scale(1.5F, 1.5F, 1);
                mc.fontRenderer.drawString(String.format(Locale.ROOT, "%02d / %02d", lives, maximum), 0, 0, color);
            } finally {
                GlStateManager.popMatrix();
            }
            if (lives == 0) { mc.fontRenderer.drawString("\u0412\u042b\u0411\u042b\u041b", 32, 42, 0xFFE54D59); }
            drawRect(10, 56, 142, 60, 0xFF303744);
            int width = Math.round(132.0F * lives / maximum);
            if (width > 0) { drawRect(10, 56, 10 + width, 60, 0xFFE54D59); }
        } finally {
            GlStateManager.popMatrix();
            // Restore through GlStateManager to keep its state cache consistent with OpenGL.
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) { GlStateManager.enableBlend(); } else { GlStateManager.disableBlend(); }
            if (texture) { GlStateManager.enableTexture2D(); } else { GlStateManager.disableTexture2D(); }
            if (alpha) { GlStateManager.enableAlpha(); } else { GlStateManager.disableAlpha(); }
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.color(previousColor.get(0), previousColor.get(1), previousColor.get(2), previousColor.get(3));
        }
    }
}