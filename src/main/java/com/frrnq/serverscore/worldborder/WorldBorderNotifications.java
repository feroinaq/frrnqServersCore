package com.frrnq.serverscore.worldborder;

import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.ResourceNoticeMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.border.EnumBorderStatus;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Observes only the overworld. WorldBorder owns the transition and its clock. */
@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID)
public final class WorldBorderNotifications implements IBorderListener {
    private static WorldBorderNotifications current;
    private final WorldBorder border;
    private double lastSize;
    private double target;
    private double speed;
    private long sampledAt;
    private boolean shrinking;

    private WorldBorderNotifications(WorldBorder border) {
        this.border = border;
        lastSize = border.getDiameter();
        shrinking = border.getStatus() == EnumBorderStatus.SHRINKING;
        target = border.getTargetSize();
        sample();
        border.addListener(this);
    }

    public static void start(MinecraftServer server) {
        stop();
        current = new WorldBorderNotifications(server.getWorld(0).getWorldBorder());
    }

    public static void stop() {
        if (current != null) {
            current.border.removeListener(current);
            current = null;
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && current != null) {
            // getDiameter finalizes an expired vanilla transition and invokes onSizeChanged.
            current.sample();
        }
    }

    private void sample() {
        sampledAt = System.currentTimeMillis();
        lastSize = border.getDiameter();
        long remaining = border.getTimeUntilTarget();
        speed = shrinking && remaining > 0 ? (lastSize - target) / remaining : 0;
    }

    @Override public void onTransitionStarted(WorldBorder border, double from, double to, long time) {
        lastSize = from;
        sampledAt = System.currentTimeMillis();
        speed = time > 0 ? (from - to) / time : 0;
        target = to;
        shrinking = to < from;
        if (shrinking) {
            if (time <= 0) {
                shrinking = false;
                send(false, 0);
            } else {
                send(true, Math.max(0, border.getTimeUntilTarget()));
            }
        }
    }

    @Override public void onSizeChanged(WorldBorder border, double size) {
        boolean completed = shrinking && Double.compare(size, target) == 0;
        // A freeze at the interpolated size cancels a transition. Compare with
        // the sampled vanilla trajectory, allowing for millisecond rounding.
        double expected = shrinking ? Math.max(target, lastSize - speed * (System.currentTimeMillis() - sampledAt)) : lastSize;
        boolean instantReduction = size < expected - Math.max(0.000001D, Math.abs(speed) * 2);
        shrinking = false;
        lastSize = size;
        target = size;
        if (completed || instantReduction) { send(false, 0); }
    }

    private static void send(boolean start, long remaining) {
        CoreNetwork.CHANNEL.sendToAll(ResourceNoticeMessage.border(start, remaining));
    }

    @Override public void onCenterChanged(WorldBorder border, double x, double z) {}
    @Override public void onWarningTimeChanged(WorldBorder border, int time) {}
    @Override public void onWarningDistanceChanged(WorldBorder border, int distance) {}
    @Override public void onDamageAmountChanged(WorldBorder border, double amount) {}
    @Override public void onDamageBufferChanged(WorldBorder border, double buffer) {}
}
