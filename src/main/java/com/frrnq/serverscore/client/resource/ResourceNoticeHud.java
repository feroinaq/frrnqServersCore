package com.frrnq.serverscore.client.resource;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import com.frrnq.serverscore.client.hud.LivesHud;
import com.frrnq.serverscore.network.ResourceNoticeMessage.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Connection-scoped state; accessed only on Minecraft's main thread. */
public final class ResourceNoticeHud extends Gui {
    private static final float SOUND_VOLUME = 1.0F;
    private static final float GAME_START_SOUND_VOLUME = 0.85F;
    private static final int SOUND_GAP_TICKS = 10;
    private static final class ScheduledTone {
        private final SoundEvent sound;
        private final float pitch;
        private int ticksLeft;
        private ScheduledTone(SoundEvent sound, float pitch, int delay) {
            this.sound = sound; this.pitch = pitch; this.ticksLeft = delay;
        }
    }
    private final List<ScheduledTone> pendingTones = new ArrayList<>();
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private NetworkManager connection;
    private String location;
    private Type type;
    public static final double BORDER_START_SECONDS = 7;
    public static final double BORDER_END_SECONDS = 5;
    private long remainingMillis;
    private long started;
    public void show(NetworkManager source, String name, Type noticeType, long remaining) {
        Minecraft mc = Minecraft.getMinecraft();
        if (name == null || (name.isEmpty() && noticeType != Type.AIRDROP && noticeType != Type.GAME_START && noticeType != Type.BORDER_START && noticeType != Type.BORDER_END) || name.length() > 48 || mc.player == null || mc.world == null) { return; }
        if (connection != source) { pendingTones.clear(); }
        connection = source; location = name; type = noticeType; started = System.nanoTime(); remainingMillis = remaining;
        // Schedule once per event. Natural note decay extends the short motif
        // to approximately 1.5-2 seconds; user volume/category settings still apply.
        if (type == Type.BORDER_START) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BASS, SOUND_VOLUME, .7F);
            for (int i = 1; i <= 4; i++) {
                pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_BASS, .7F + i * .05F, SOUND_GAP_TICKS * i));
            }
            return;
        }
        if (type == Type.BORDER_END) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_CHIME, SOUND_VOLUME, 1.0F);
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_PLING, 1.2F, SOUND_GAP_TICKS));
            return;
        }
        if (type == Type.GAME_START) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_CHIME, GAME_START_SOUND_VOLUME, 0.9F);
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_HARP, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_CHIME, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 2));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_PLING, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 3));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_HARP, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 4));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_CHIME, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 5));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_PLING, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 6));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_CHIME, GAME_START_SOUND_VOLUME, SOUND_GAP_TICKS * 7));
            return;
        }
        if (type == Type.AIRDROP) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_CHIME, SOUND_VOLUME, .85F);
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_HARP, 1.05F, SOUND_GAP_TICKS));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_CHIME, 1.25F, SOUND_GAP_TICKS * 2));
            return;
        }
        boolean warn = type == Type.WARNING;
        mc.player.playSound(SoundEvents.BLOCK_NOTE_HARP, SOUND_VOLUME, warn ? .9F : 1.0F);
        if (warn) {
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_HARP, 1.05F, SOUND_GAP_TICKS));
        } else {
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_PLING, 1.1F, SOUND_GAP_TICKS));
            pendingTones.add(new ScheduledTone(SoundEvents.BLOCK_NOTE_PLING, 1.25F, SOUND_GAP_TICKS * 2));
        }
    }
    private void clear() { connection = null; location = null; started = 0; pendingTones.clear(); }
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        NetworkManager old = event.getManager();
        Minecraft.getMinecraft().addScheduledTask(() -> { if (connection == old) { clear(); } });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.getConnection().getNetworkManager() != connection
                || (started != 0 && System.nanoTime() - started >= durationNanos())) { clear(); return; }
        if (mc.player == null || mc.world == null) { pendingTones.clear(); return; }
        // Main-thread, finite delays: each queued note is removed after one playback.
        for (Iterator<ScheduledTone> iterator = pendingTones.iterator(); iterator.hasNext();) {
            ScheduledTone tone = iterator.next();
            if (--tone.ticksLeft <= 0) {
                iterator.remove();
                mc.player.playSound(tone.sound, SOUND_VOLUME, tone.pitch);
            }
        }
    }
    private double durationSeconds() {
        if (type == Type.BORDER_START) { return BORDER_START_SECONDS; }
        if (type == Type.BORDER_END) { return BORDER_END_SECONDS; }
        return type == Type.GAME_START ? 7 : 5;
    }
    private long durationNanos() { return (long) (durationSeconds() * 1000000000L); }
    private static int tint(int rgb, int alpha) { return (alpha << 24) | rgb; }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || location == null
                || mc.world == null || mc.player == null || mc.gameSettings.hideGUI || mc.getConnection() == null
                || mc.getConnection().getNetworkManager() != connection) { return; }
        double age = (System.nanoTime() - started) / 1000000000.0;
        double duration = durationSeconds();
        double fade = type == Type.GAME_START ? .45 : .35;
        float opacity = (float) Math.max(0, Math.min(1, Math.min(age / fade, (duration - age) / fade)));
        int alpha = Math.round(255 * opacity);
        if (alpha < 4) { return; }
        int width = event.getResolution().getScaledWidth(), height = event.getResolution().getScaledHeight();
        float livesScale = Math.min(LivesHud.HUD_SCALE, Math.min(width / 160.0F, height / 74.0F));
        int top = (int) Math.ceil(74 * livesScale) + 6
                + com.frrnq.serverscore.client.hud.BorderWarningHud.reservedHeight(mc, width);
        int panelHeight = type == Type.BORDER_START || type == Type.BORDER_END ? 112 : 80;
        float scale = Math.min(1, Math.min((width - 8) / 320.0F, (height - top - 8) / (float) panelHeight));
        if (scale <= 0) { return; }
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), test = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        color.clear(); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((width - 320 * scale) / 2, top - 6 * (1 - opacity), 0);
            GlStateManager.scale(scale, scale, 1);
            drawRect(0, 0, 320, panelHeight, tint(0x10141C, Math.round(190 * opacity)));
            int accent = type == Type.AIRDROP ? 0x55D7D6 : 0xE04B55;
            drawRect(0, 0, 2, panelHeight, tint(accent, alpha));
            // A small pixel resource-crate icon, with the same fade as the panel.
            drawRect(10, 24, 31, 43, tint(0xB8BDC8, alpha));
            drawRect(12, 26, 29, 41, tint(0x303744, alpha));
            drawRect(19, 25, 22, 42, tint(accent, alpha));
            drawRect(11, 31, 30, 34, tint(accent, alpha));
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (type == Type.BORDER_START || type == Type.BORDER_END) {
                mc.fontRenderer.drawString("FRRNQ SERVERS", 40, 8, tint(0xB8BDC8, alpha));
                boolean shrinking = type == Type.BORDER_START;
                String heading = shrinking ? "\u0412\u041d\u0418\u041c\u0410\u041d\u0418\u0415! \u0411\u0410\u0420\u042c\u0415\u0420" : "\u0421\u0423\u0416\u0415\u041d\u0418\u0415 \u0417\u0410\u0412\u0415\u0420\u0428\u0415\u041d\u041e";
                String main = shrinking ? "\u0413\u0440\u0430\u043d\u0438\u0446\u0430 \u0438\u0433\u0440\u043e\u0432\u043e\u0433\u043e \u043c\u0438\u0440\u0430 \u0441\u0443\u0436\u0430\u0435\u0442\u0441\u044f!" : "\u0413\u0440\u0430\u043d\u0438\u0446\u0430 \u0438\u0433\u0440\u043e\u0432\u043e\u0433\u043e \u043c\u0438\u0440\u0430 \u0434\u043e\u0441\u0442\u0438\u0433\u043b\u0430 \u043d\u043e\u0432\u043e\u0433\u043e \u0440\u0430\u0437\u043c\u0435\u0440\u0430.";
                String help = shrinking ? "\u041e\u0441\u0442\u0430\u0432\u0430\u0439\u0442\u0435\u0441\u044c \u0432\u043d\u0443\u0442\u0440\u0438 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e\u0439 \u0437\u043e\u043d\u044b." : "\u041e\u0441\u0442\u0430\u0432\u0430\u0439\u0442\u0435\u0441\u044c \u0432 \u043f\u0440\u0435\u0434\u0435\u043b\u0430\u0445 \u0431\u0430\u0440\u044c\u0435\u0440\u0430.";
                mc.fontRenderer.drawString(heading, 40, 23, tint(accent, alpha));
                int y = 39;
                for (String line : mc.fontRenderer.listFormattedStringToWidth(main, 270)) {
                    mc.fontRenderer.drawString(line, 40, y, tint(0xF5F0F5, alpha)); y += 10;
                }
                for (String line : mc.fontRenderer.listFormattedStringToWidth(help, 270)) {
                    mc.fontRenderer.drawString(line, 40, y + 4, tint(0xF5F0F5, alpha)); y += 10;
                }
                if (shrinking) {
                    long seconds = (long) Math.ceil(Math.max(0, remainingMillis / 1000.0 - age));
                    mc.fontRenderer.drawString("\u0414\u043e \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u044f: " + seconds + " \u0441", 40, y + 10, tint(0xB8BDC8, alpha));
                }
            } else if (type == Type.GAME_START) {
                mc.fontRenderer.drawString("FRRNQ SERVERS \u00b7 GAME START", 40, 8, tint(0xB8BDC8, alpha));
                mc.fontRenderer.drawString("\u0418\u0413\u0420\u0410 \u041d\u0410\u0427\u0410\u041b\u0410\u0421\u042c!", 40, 23, tint(accent, alpha));
                mc.fontRenderer.drawString("\u0416\u0435\u043b\u0430\u0435\u043c \u0432\u0441\u0435\u043c \u0438\u0433\u0440\u043e\u043a\u0430\u043c \u0443\u0434\u0430\u0447\u0438!", 40, 43, tint(0xF5F0F5, alpha));
                mc.fontRenderer.drawString("\u041f\u0443\u0441\u0442\u044c \u043f\u043e\u0431\u0435\u0434\u0438\u0442 \u0441\u0438\u043b\u044c\u043d\u0435\u0439\u0448\u0430\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u0430.", 40, 56, tint(0xF5F0F5, alpha));
            } else {
                mc.fontRenderer.drawString(type == Type.AIRDROP ? "FRRNQ SERVERS \u00b7 AIRDROP" : "FRRNQ SERVERS", 40, 8, tint(0xB8BDC8, alpha));
                int y = 23;
                List<String> names = mc.fontRenderer.listFormattedStringToWidth(type == Type.AIRDROP ? "\u041e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d \u043d\u043e\u0432\u044b\u0439 AirDrop!" : location, 270);
                for (String line : names) { mc.fontRenderer.drawString(line, 40, y, tint(0xF5F0F5, alpha)); y += 10; }
                y += 4;
                String message = type == Type.AIRDROP ? "\u041d\u0430\u0439\u0434\u0438\u0442\u0435 \u0441\u0432\u0435\u0442\u043e\u0432\u043e\u0439 \u043b\u0443\u0447 \u0438 \u0437\u0430\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0435\u0441\u0443\u0440\u0441\u044b." : type == Type.WARNING ? "\u0420\u0435\u0441\u0443\u0440\u0441\u044b \u043e\u0431\u043d\u043e\u0432\u044f\u0442\u0441\u044f \u0447\u0435\u0440\u0435\u0437 1 \u043c\u0438\u043d\u0443\u0442\u0443!" : "\u0420\u0435\u0441\u0443\u0440\u0441\u044b \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u044b!";
                for (String line : mc.fontRenderer.listFormattedStringToWidth(message, 270)) {
                    mc.fontRenderer.drawString(line, 40, y, tint(0xF5F0F5, alpha)); y += 10;
                }
            }
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
