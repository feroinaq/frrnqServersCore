package com.frrnq.serverscore.client.hud;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import com.frrnq.serverscore.network.TeamStatusMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.NetworkManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class TeamStatusHud extends Gui {
    public static final float HUD_SCALE = LivesHud.HUD_SCALE;
    private static final int PANEL_WIDTH = 152;
    private static final int ROW_HEIGHT = 16;
    private static final int PANEL_TOP = 76;
    private final FloatBuffer previousColor = BufferUtils.createFloatBuffer(16);
    private NetworkManager connection;
    private World world;
    private TeamStatusMessage state;

    public void update(NetworkManager source, TeamStatusMessage message) {
        connection = source;
        world = Minecraft.getMinecraft().world;
        state = message;
    }
    private void clear() { connection = null; world = null; state = null; }
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (connection == event.getManager()) { clear(); }
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection || mc.world != world) { clear(); }
    }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || state == null || !state.active
                || mc.player == null || mc.world != world || mc.gameSettings.hideGUI
                || mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection) { return; }
        int width = event.getResolution().getScaledWidth(), height = event.getResolution().getScaledHeight();
        float livesScale = Math.min(LivesHud.HUD_SCALE, Math.min(width / 160.0F, height / 74.0F));
        int panelHeight = 26 + state.rows.size() * ROW_HEIGHT;
        float scale = Math.min(HUD_SCALE, Math.min(width / 160.0F, (height - PANEL_TOP * livesScale - 4) / panelHeight));
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
            GlStateManager.translate(4 * livesScale, PANEL_TOP * livesScale, 0);
            GlStateManager.scale(scale, scale, 1);
            drawRect(0, 0, PANEL_WIDTH, panelHeight, 0xB010141C);
            drawRect(0, 0, 2, panelHeight, 0xFFE04B55);
            mc.fontRenderer.drawString("\u041a\u041e\u041c\u0410\u041d\u0414\u042b", 10, 7, 0xFFB8BDC8);
            int y = 24;
            for (TeamStatusMessage.Row row : state.rows) {
                int color = 0xFF000000 | row.color;
                // A small filled circle in the same pixel geometry as the lives icon.
                drawRect(11, y + 1, 15, y + 7, color);
                drawRect(10, y + 2, 16, y + 6, color);
                String count = Integer.toString(row.count);
                int countX = PANEL_WIDTH - 10 - mc.fontRenderer.getStringWidth(count);
                String name = mc.fontRenderer.trimStringToWidth(row.name, Math.max(0, countX - 28));
                mc.fontRenderer.drawString(name, 22, y, 0xFFF5F0F5);
                mc.fontRenderer.drawString(count, countX, y, 0xFFF5F0F5);
                y += ROW_HEIGHT;
            }
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
