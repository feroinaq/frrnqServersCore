package com.frrnq.serverscore.worldrules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class PvPProtection {
    private final WorldRulesConfig settings;

    public PvPProtection(WorldRulesConfig settings) {
        this.settings = settings;
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (!(victim instanceof EntityPlayer) || victim.world.isRemote
                || !settings.isPvpProtectionEnabled()
                || victim.world.provider.getDimension() != settings.getDimensionId()
                || victim.posY <= settings.getMaxPvpY()) {
            return;
        }
        EntityPlayer attacker = getAttacker(event.getSource());
        if (attacker != null && attacker != victim) {
            event.setCanceled(true);
        }
    }

    private EntityPlayer getAttacker(DamageSource source) {
        Entity responsible = source.getTrueSource();
        if (responsible instanceof EntityPlayer) {
            return (EntityPlayer) responsible;
        }
        Entity immediate = source.getImmediateSource();
        if (immediate instanceof EntityPlayer) {
            return (EntityPlayer) immediate;
        }
        // Fallback for sources that expose a vanilla projectile but omit its owner.
        Entity owner = null;
        if (immediate instanceof EntityArrow) {
            owner = ((EntityArrow) immediate).shootingEntity;
        } else if (immediate instanceof EntityThrowable) {
            owner = ((EntityThrowable) immediate).getThrower();
        }
        return owner instanceof EntityPlayer ? (EntityPlayer) owner : null;
    }
}