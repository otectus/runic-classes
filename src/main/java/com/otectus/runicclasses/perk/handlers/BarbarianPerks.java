package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.BarbarianKnockback;
import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/** Barbarian (Strength / Constitution) — Fury. */
public final class BarbarianPerks implements ClassModule {

    static final String BLOODIED_RESOLVE = "cls_barbarian_bloodied_resolve";
    static final String RECKLESS_SWING = "cls_barbarian_reckless_swing";
    static final String STUBBORN_RENEWAL = "cls_barbarian_stubborn_renewal";
    static final String UNSTOPPABLE = "cls_barbarian_unstoppable";
    static final String TITANS_FURY = "cls_barbarian_titans_fury";
    // Thick Hide is an attribute (PerkAttributes).

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee) return;
        ServerPlayer p = hit.member;
        if (Perks.on(p, BLOODIED_RESOLVE) && p.getHealth() <= p.getMaxHealth() * Perks.pct(BLOODIED_RESOLVE, 0)) {
            hit.addPercent(Perks.pct(BLOODIED_RESOLVE, 1));
        }
        if (Perks.on(p, RECKLESS_SWING) && p.isSprinting()) {
            hit.addPercent(Perks.pct(RECKLESS_SWING, 0));
            BarbarianKnockback.mark(hit.target, hit.now, (float) (1.0 + Perks.pct(RECKLESS_SWING, 1)));
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (Perks.on(p, STUBBORN_RENEWAL) && hit.crossesBelow(Perks.pct(STUBBORN_RENEWAL, 0)) && Perks.ready(p, STUBBORN_RENEWAL)) {
            Perks.startCooldown(p, STUBBORN_RENEWAL, Perks.ticks(STUBBORN_RENEWAL, 2));
            int duration = Perks.ticks(STUBBORN_RENEWAL, 1);
            Deferred.later(() -> {
                if (p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
            });
        }
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!(victim instanceof Enemy) || !Perks.on(member, UNSTOPPABLE)) return;
        int duration = Perks.ticks(UNSTOPPABLE, 0);
        member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0));
        member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
    }

    @Override
    public boolean survive(ServerPlayer member, LivingDeathEvent event) {
        if (!Perks.on(member, TITANS_FURY) || Perks.unsurvivable(event.getSource())) return false;
        if (Perks.surviveLocked(member) || !Perks.ready(member, TITANS_FURY)) return false;
        member.setHealth(1.0f);
        member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Perks.ticks(TITANS_FURY, 1), 1));
        Perks.startCooldown(member, TITANS_FURY, Perks.ticks(TITANS_FURY, 0));
        Perks.lockSurvive(member);
        return true;
    }
}
