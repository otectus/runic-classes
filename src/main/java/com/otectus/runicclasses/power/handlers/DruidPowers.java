package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.PerkAttributes;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Druid Powers. Mark: Vine Lash; Seal: Thorned Mantle; Crown: Heart of the Grove. */
public final class DruidPowers implements ClassModule {

    static final String VINE_LASH = "cls_druid_vine_lash";
    static final String THORNED_MANTLE = "cls_druid_thorned_mantle";
    static final String HEART_OF_THE_GROVE = "cls_druid_heart_of_the_grove";

    @Override
    public void outgoing(Hit hit) {
        if (!(hit.melee || hit.projectile || hit.magic)) return;
        if (PerkAttributes.onNaturalGround(hit.member) && Powers.trigger(hit.member, VINE_LASH, hit.now, hit.target)) {
            hit.target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Powers.ticks(VINE_LASH, "duration_ticks", 60), 1), hit.member);
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (hit.fraction() >= Powers.value(THORNED_MANTLE, "hit_fraction", 0.15) && hit.healthAfter() > 0
                && Powers.trigger(p, THORNED_MANTLE, hit.now, p)) {
            float damage = (float) Powers.value(THORNED_MANTLE, "damage", 4.0);
            int poison = Powers.ticks(THORNED_MANTLE, "duration_ticks", 60);
            double radius = Powers.value(THORNED_MANTLE, "radius_blocks", 3.0);
            Deferred.later(() -> {
                for (LivingEntity enemy : Combat.enemiesNear(p, p.position(), radius)) {
                    Combat.secondaryHurt(p, enemy, p.damageSources().thorns(p), damage);
                    enemy.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0), p);
                }
            });
        }
        if (hit.crossesBelow(Powers.value(HEART_OF_THE_GROVE, "health_threshold", 0.30))
                && Powers.trigger(p, HEART_OF_THE_GROVE, hit.now, p)) {
            int regen = Powers.ticks(HEART_OF_THE_GROVE, "duration_ticks", 100);
            int root = Powers.ticks(HEART_OF_THE_GROVE, "root_ticks", 40);
            double radius = Powers.value(HEART_OF_THE_GROVE, "radius_blocks", 5.0);
            Deferred.later(() -> {
                if (!p.isAlive()) return;
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 2));
                for (LivingEntity enemy : Combat.enemiesNear(p, p.position(), radius)) {
                    enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, root, 4), p);
                }
            });
        }
    }
}
