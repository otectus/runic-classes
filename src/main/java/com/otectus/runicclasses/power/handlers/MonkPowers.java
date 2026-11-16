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

/** Monk Powers. Mark: Opening Palm; Seal: Quivering Strike; Crown: Perfect Form. */
public final class MonkPowers implements ClassModule {

    static final String OPENING_PALM = "cls_monk_opening_palm";
    static final String QUIVERING_STRIKE = "cls_monk_quivering_strike";
    static final String PERFECT_FORM = "cls_monk_perfect_form";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee || !Combat.unarmed(hit.member)) return;
        ServerPlayer p = hit.member;
        CombatMemory memory = CombatMemory.of(p);
        long last = memory.lastOn("palm", hit.target);
        memory.markOn("palm", hit.target, hit.now);
        if (hit.now - last >= Powers.ticks(OPENING_PALM, "fresh_ticks", 100) && Powers.trigger(p, OPENING_PALM, hit.now, hit.target)) {
            hit.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Powers.ticks(OPENING_PALM, "duration_ticks", 60), 0), p);
        }

        // Consecutive unarmed hits on one target, the chain broken by a five-second gap or a new target.
        if (hit.target.getUUID().equals(memory.streakTarget) && hit.now - memory.streakLastTick <= 100) {
            memory.streakCount++;
        } else {
            memory.streakTarget = hit.target.getUUID();
            memory.streakCount = 1;
        }
        memory.streakLastTick = hit.now;
        if (memory.streakCount % Math.max(1, Powers.ticks(QUIVERING_STRIKE, "hits", 5)) == 0
                && Powers.trigger(p, QUIVERING_STRIKE, hit.now, hit.target)) {
            double bonus = Math.min(Powers.value(QUIVERING_STRIKE, "damage_cap", 20.0),
                    hit.target.getMaxHealth() * Powers.value(QUIVERING_STRIKE, "health_fraction", 0.10));
            hit.addFlat(bonus);
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (!Combat.noHeavyArmor(p) || hit.fraction() < Powers.value(PERFECT_FORM, "hit_fraction", 0.40)) return;
        if (!Powers.trigger(p, PERFECT_FORM, hit.now, p)) return;
        hit.setAmount(0.0f);
        int duration = Powers.ticks(PERFECT_FORM, "duration_ticks", 80);
        Deferred.later(() -> {
            if (p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1));
        });
    }
}
