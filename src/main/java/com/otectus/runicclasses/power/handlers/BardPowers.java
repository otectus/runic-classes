package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Bard Powers. Mark: Opening Verse; Seal: Dissonance; Crown: Anthem. */
public final class BardPowers implements ClassModule {

    static final String OPENING_VERSE = "cls_bard_opening_verse";
    static final String DISSONANCE = "cls_bard_dissonance";
    static final String ANTHEM = "cls_bard_anthem";

    @Override
    public void outgoing(Hit hit) {
        if (!(hit.melee || hit.projectile || hit.magic)) return;
        ServerPlayer p = hit.member;
        // The dispatcher stamps this hit as combat after the modules run, so this measures the lull before it.
        if (hit.now - CombatMemory.of(p).lastCombatTick >= Powers.ticks(OPENING_VERSE, "idle_ticks", 200)
                && Powers.trigger(p, OPENING_VERSE, hit.now, p)) {
            int duration = Powers.ticks(OPENING_VERSE, "duration_ticks", 80);
            for (LivingEntity ally : Combat.alliesNear(p, p.position(), Powers.value(OPENING_VERSE, "radius_blocks", 8.0), true)) {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0));
            }
        }
        if (Powers.trigger(p, DISSONANCE, hit.now, hit.target)) {
            int duration = Powers.ticks(DISSONANCE, "duration_ticks", 80);
            for (LivingEntity enemy : Combat.enemiesNear(p, hit.target.position(), Powers.value(DISSONANCE, "radius_blocks", 4.0))) {
                enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0), p);
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0), p);
            }
        }
    }

    @Override
    public void allyDamaged(ServerPlayer member, LivingEntity ally, FinalHit hit) {
        if (member.distanceTo(ally) > Powers.value(ANTHEM, "radius_blocks", 10.0)) return;
        if (!hit.crossesBelow(Powers.value(ANTHEM, "health_threshold", 0.30))) return;
        if (!Powers.trigger(member, ANTHEM, hit.now, ally)) return;
        int duration = Powers.ticks(ANTHEM, "duration_ticks", 120);
        Deferred.later(() -> {
            if (!ally.isAlive()) return;
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 1));
        });
    }
}
