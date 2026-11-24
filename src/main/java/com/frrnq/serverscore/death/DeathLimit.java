package com.frrnq.serverscore.death;

import net.minecraftforge.common.MinecraftForge;

public final class DeathLimit {
    public DeathLimit() {
        // In Forge 1.12.2 FML game events and Forge living events share EVENT_BUS.
        MinecraftForge.EVENT_BUS.register(new DeathHandler());
        MinecraftForge.EVENT_BUS.register(new DeathRespawn());
    }
}