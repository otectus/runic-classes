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
import net.minecraftforge.event.entity.living.ShieldBlockEvent;

/** Paladin Powers. Mark: Righteous Blow; Seal: Aegis; Crown: Avatar of the Oath. */
public final class PaladinPowers implements ClassModule {

    static final String RIGHTEOUS_BLOW = "cls_paladin_righteous_blow";
    static final String AEGIS = "cls_paladin_aegis";
    static final String AVATAR = "cls_paladin_avatar_of_the_oath";

    @Override
    public void outgoing(Hit hit) {
        if (!(hit.melee || hit.projectile || hit.magic)) return;
        long window = Powers.ticks(RIGHTEOUS_BLOW, "window_ticks", 100);
        CombatMemory memory = CombatMemory.of(hit.member);
        // "First hit": once per enemy per window, then the enemy must wound someone again.
        if (!CombatMemory.woundedRecently(hit.target, hit.now, window) || hit.now - memory.lastOn("righteous", hit.target) <= window) return;
        if (Powers.trigger(hit.member, RIGHTEOUS_BLOW, hit.now, hit.target)) {
            memory.markOn("righteous", hit.target, hit.now);
            hit.addPercent(Powers.value(RIGHTEOUS_BLOW, "damage_bonus", 0.25));
        }
    }

    @Override
    public void blocked(ServerPlayer member, ShieldBlockEvent event) {
        long now = member.level().getGameTime();
        CombatMemory memory = CombatMemory.of(member);
        int blocks = memory.recordWithin("aegis", now, Powers.ticks(AEGIS, "window_ticks", 100));
        if (blocks < Math.max(1, Powers.ticks(AEGIS, "blocks", 3)) || !Powers.trigger(member, AEGIS, now, member)) return;
        memory.clearWithin("aegis");
        int duration = Powers.ticks(AEGIS, "duration_ticks", 80);
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Powers.value(AEGIS, "radius_blocks", 6.0), true)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (!hit.crossesBelow(Powers.value(AVATAR, "health_threshold", 0.25)) || !Powers.trigger(p, AVATAR, hit.now, p)) return;
        int duration = Powers.ticks(AVATAR, "duration_ticks", 160);
        CombatMemory.of(p).open("avatar", hit.now + duration);
        Deferred.later(() -> {
            if (p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 2));
        });
    }

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!(hit.attacker instanceof ServerPlayer p) || !CombatMemory.of(p).isOpen("avatar", hit.now)) return;
        float heal = (float) Powers.value(AVATAR, "heal", 2.0);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity ally : Combat.alliesNear(p, p.position(), Powers.value(AVATAR, "radius_blocks", 8.0), false)) {
            double d = ally.distanceToSqr(p);
            if (d < best) {
                best = d;
                nearest = ally;
            }
        }
        (nearest != null ? nearest : p).heal(heal);
    }
}
