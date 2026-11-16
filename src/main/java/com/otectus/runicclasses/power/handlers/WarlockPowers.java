package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Warlock Powers. Mark: Soul Leech; Seal: Patron's Shadow; Crown: The Debt Comes Due. */
public final class WarlockPowers implements ClassModule {

    static final String SOUL_LEECH = "cls_warlock_soul_leech";
    static final String PATRONS_SHADOW = "cls_warlock_patrons_shadow";
    static final String DEBT = "cls_warlock_the_debt_comes_due";

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!(hit.attacker instanceof ServerPlayer p) || !hit.victim.hasEffect(MobEffects.WEAKNESS)) return;
        if (Powers.trigger(p, SOUL_LEECH, hit.now, hit.victim)) p.heal((float) Powers.value(SOUL_LEECH, "heal", 1.0));
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!(victim instanceof Enemy) || !Powers.trigger(member, PATRONS_SHADOW, member.level().getGameTime(), member)) return;
        int duration = Powers.ticks(PATRONS_SHADOW, "duration_ticks", 60);
        for (LivingEntity enemy : Combat.enemiesNear(member, victim.position(), Powers.value(PATRONS_SHADOW, "radius_blocks", 5.0))) {
            enemy.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0), member);
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0), member);
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (!hit.crossesBelow(Powers.value(DEBT, "health_threshold", 0.20)) || !Powers.trigger(p, DEBT, hit.now, p)) return;
        float damage = (float) Powers.value(DEBT, "damage", 6.0);
        float healPer = (float) Powers.value(DEBT, "heal_per_enemy", 3.0);
        double radius = Powers.value(DEBT, "radius_blocks", 8.0);
        Deferred.later(() -> {
            if (!p.isAlive()) return;
            int struck = 0;
            for (LivingEntity enemy : Combat.enemiesNear(p, p.position(), radius)) {
                if (Combat.secondaryHurt(p, enemy, p.damageSources().indirectMagic(p, p), damage)) struck++;
            }
            if (struck > 0) p.heal(healPer * struck);
        });
    }
}
