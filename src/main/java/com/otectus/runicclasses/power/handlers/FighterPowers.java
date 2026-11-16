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

/** Fighter Powers. Mark: Measured Cut; Seal: Bulwark Call; Crown: Last Stand. */
public final class FighterPowers implements ClassModule {

    static final String MEASURED_CUT = "cls_fighter_measured_cut";
    static final String BULWARK_CALL = "cls_fighter_bulwark_call";
    static final String LAST_STAND = "cls_fighter_last_stand";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee) return;
        CombatMemory memory = CombatMemory.of(hit.member);
        // The dispatcher records this hit's time after the modules run, so this is the previous swing.
        if (hit.now - memory.lastAttackTick >= Powers.ticks(MEASURED_CUT, "idle_ticks", 100)
                && Powers.trigger(hit.member, MEASURED_CUT, hit.now, hit.target)) {
            hit.addPercent(Powers.value(MEASURED_CUT, "damage_bonus", 0.30));
        }
    }

    @Override
    public void blocked(ServerPlayer member, ShieldBlockEvent event) {
        long now = member.level().getGameTime();
        if (!Powers.trigger(member, BULWARK_CALL, now, member)) return;
        int duration = Powers.ticks(BULWARK_CALL, "duration_ticks", 80);
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Powers.value(BULWARK_CALL, "radius_blocks", 4.0), true)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0));
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (!hit.crossesBelow(Powers.value(LAST_STAND, "health_threshold", 0.25))) return;
        if (!Powers.trigger(p, LAST_STAND, hit.now, p)) return;
        int duration = Powers.ticks(LAST_STAND, "duration_ticks", 200);
        CombatMemory.of(p).open("last_stand", hit.now + duration);
        Deferred.later(() -> {
            if (p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 3));
        });
    }

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!hit.melee || !(hit.attacker instanceof ServerPlayer p)) return;
        if (CombatMemory.of(p).isOpen("last_stand", hit.now)) p.heal((float) Powers.value(LAST_STAND, "heal_per_hit", 1.0));
    }
}
