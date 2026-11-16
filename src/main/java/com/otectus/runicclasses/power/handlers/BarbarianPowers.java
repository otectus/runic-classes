package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Barbarian Powers. Mark: Fury Rising; Seal: Blood Roar; Crown: Unbroken. Magnitudes are Runic Skills
 * Power-override keys ({@code data/runicclasses/powers/<id>.json} {@code values}).
 */
public final class BarbarianPowers implements ClassModule {

    static final String FURY_RISING = "cls_barbarian_fury_rising";
    static final String BLOOD_ROAR = "cls_barbarian_blood_roar";
    static final String UNBROKEN = "cls_barbarian_unbroken";

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (hit.melee && hit.leavesAtOrBelow(Powers.value(FURY_RISING, "health_threshold", 0.5))
                && Powers.trigger(p, FURY_RISING, hit.now, hit.attacker)) {
            CombatMemory.of(p).open("fury_rising", hit.now + Powers.ticks(FURY_RISING, "window_ticks", 80));
        }
        if (hit.fraction() >= Powers.value(UNBROKEN, "hit_fraction", 0.30) && hit.healthAfter() > 0
                && Powers.trigger(p, UNBROKEN, hit.now, p)) {
            int duration = Powers.ticks(UNBROKEN, "duration_ticks", 120);
            Deferred.later(() -> {
                if (!p.isAlive()) return;
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1));
            });
        }
    }

    @Override
    public void outgoing(Hit hit) {
        if (hit.melee && CombatMemory.of(hit.member).consume("fury_rising", hit.now)) {
            hit.addPercent(Powers.value(FURY_RISING, "damage_bonus", 0.20));
        }
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!(victim instanceof Enemy)) return;
        if (member.getHealth() > member.getMaxHealth() * Powers.value(BLOOD_ROAR, "health_threshold", 0.5)) return;
        long now = member.level().getGameTime();
        if (!Powers.trigger(member, BLOOD_ROAR, now, member)) return;
        member.heal((float) (member.getMaxHealth() * Powers.value(BLOOD_ROAR, "heal_fraction", 0.10)));
        double radius = Powers.value(BLOOD_ROAR, "radius_blocks", 3.0);
        for (LivingEntity enemy : Combat.enemiesNear(member, member.position(), radius)) {
            enemy.knockback(1.0, member.getX() - enemy.getX(), member.getZ() - enemy.getZ());
        }
    }
}
