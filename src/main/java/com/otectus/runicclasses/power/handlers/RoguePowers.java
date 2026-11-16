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

import java.util.Iterator;

/** Rogue Powers. Mark: Opening Cut; Seal: Vanish; Crown: Deathmark. */
public final class RoguePowers implements ClassModule {

    static final String OPENING_CUT = "cls_rogue_opening_cut";
    static final String VANISH = "cls_rogue_vanish";
    static final String DEATHMARK = "cls_rogue_deathmark";

    @Override
    public void outgoing(Hit hit) {
        if (!(hit.melee || hit.projectile)) return;
        if (hit.target.getHealth() >= hit.target.getMaxHealth() && Powers.trigger(hit.member, OPENING_CUT, hit.now, hit.target)) {
            int duration = Powers.ticks(OPENING_CUT, "duration_ticks", 80);
            hit.target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0), hit.member);
            hit.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0), hit.member);
        }
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!(victim instanceof Enemy) || !Powers.trigger(member, VANISH, member.level().getGameTime(), member)) return;
        int duration = Powers.ticks(VANISH, "duration_ticks", 80);
        member.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0));
        member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0));
    }

    /** Four hits on one target inside the window detonate a share of the damage dealt in it. */
    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!(hit.attacker instanceof ServerPlayer p) || !Powers.ready(p, DEATHMARK)) return;
        CombatMemory memory = CombatMemory.of(p);
        long window = Powers.ticks(DEATHMARK, "window_ticks", 100);
        if (!hit.victim.getUUID().equals(memory.streakTarget)) {
            memory.deathmarkLog.clear();
            memory.streakTarget = hit.victim.getUUID();
        }
        memory.deathmarkLog.addLast(new double[]{hit.now, hit.amount()});
        for (Iterator<double[]> it = memory.deathmarkLog.iterator(); it.hasNext(); ) {
            if (hit.now - (long) it.next()[0] > window) it.remove();
        }
        if (memory.deathmarkLog.size() < Math.max(1, Powers.ticks(DEATHMARK, "hits", 4))) return;
        double dealt = 0;
        for (double[] entry : memory.deathmarkLog) dealt += entry[1];
        memory.deathmarkLog.clear();
        if (!Powers.startCooldown(p, DEATHMARK, hit.now)) return;
        Powers.proc(p, DEATHMARK, hit.victim);
        float detonation = (float) Math.min(Powers.value(DEATHMARK, "damage_cap", 20.0), dealt * Powers.value(DEATHMARK, "fraction", 0.25));
        LivingEntity target = hit.victim;
        Deferred.later(() -> Combat.secondaryHurt(p, target, p.damageSources().indirectMagic(p, p), detonation));
    }
}
