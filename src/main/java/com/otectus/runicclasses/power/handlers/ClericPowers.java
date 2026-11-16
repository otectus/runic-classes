package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Powers;
import com.otectus.runicclasses.registry.RCEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;

/**
 * Cleric Powers. Mark: Radiant Burst; Seal: Ward of Light; Crown: Beacon. Beacon is lit by a healing
 * spell on an ally (Iron's Spells' {@code SpellHealEvent}) or by a splash or lingering healing potion the
 * Cleric threw breaking near an ally; for its duration, allies in range keep the Beacon effect (which the
 * dispatcher turns into bonus damage) and Regeneration I.
 */
public final class ClericPowers implements ClassModule {

    static final String RADIANT_BURST = "cls_cleric_radiant_burst";
    static final String WARD_OF_LIGHT = "cls_cleric_ward_of_light";
    static final String BEACON = "cls_cleric_beacon";

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!Combat.isUndead(victim) || !Powers.trigger(member, RADIANT_BURST, member.level().getGameTime(), member)) return;
        member.heal((float) Powers.value(RADIANT_BURST, "self_heal", 2.0));
        float allyHeal = (float) Powers.value(RADIANT_BURST, "ally_heal", 1.0);
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Powers.value(RADIANT_BURST, "radius_blocks", 4.0), false)) {
            ally.heal(allyHeal);
        }
    }

    @Override
    public void allyDamaged(ServerPlayer member, LivingEntity ally, FinalHit hit) {
        if (member.distanceTo(ally) > Powers.value(WARD_OF_LIGHT, "radius_blocks", 8.0)) return;
        if (hit.fraction() < Powers.value(WARD_OF_LIGHT, "hit_fraction", 0.20) || hit.healthAfter() <= 0) return;
        if (!Powers.trigger(member, WARD_OF_LIGHT, hit.now, ally)) return;
        int duration = Powers.ticks(WARD_OF_LIGHT, "duration_ticks", 100);
        Deferred.later(() -> {
            if (ally.isAlive()) ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 0));
        });
    }

    @Override
    public void spellHealed(ServerPlayer member, LivingEntity target) {
        if (target != member && Combat.isAlly(member, target)) light(member);
    }

    @Override
    public void projectileImpact(ServerPlayer member, ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        boolean healing = PotionUtils.getMobEffects(potion.getItem()).stream().anyMatch(e -> e.getEffect() == MobEffects.HEAL);
        if (!healing) return;
        Vec3 impact = event.getRayTraceResult().getLocation();
        if (!Combat.alliesNear(member, impact, 4.0, false).isEmpty()) light(member);
    }

    private static void light(ServerPlayer member) {
        long now = member.level().getGameTime();
        if (!Powers.trigger(member, BEACON, now, member)) return;
        CombatMemory.of(member).open("beacon", now + Powers.ticks(BEACON, "duration_ticks", 160));
        refresh(member);
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        if (CombatMemory.of(member).isOpen("beacon", now)) refresh(member);
    }

    private static void refresh(ServerPlayer member) {
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Powers.value(BEACON, "radius_blocks", 8.0), true)) {
            ally.addEffect(new MobEffectInstance(RCEffects.BEACON.get(), 30, 0, true, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true, true));
        }
    }
}
