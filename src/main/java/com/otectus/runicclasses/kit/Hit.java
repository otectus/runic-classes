package com.otectus.runicclasses.kit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * One hit before armour, as one class sees it. Outgoing bonuses accumulate additively and are added
 * once, as {@code flat + (unscaled + flat) * percent} on top of the current amount, where
 * {@code unscaled} is the amount before other mods' percentages: so they stack additively with those
 * too. Incoming reductions accumulate the same way but multiply the current amount,
 * {@code amount * (1 - reduction)}, never below zero and never past 90%: stacked with other mods'
 * reductions they diminish rather than add up to immunity.
 */
public final class Hit {

    public final ServerPlayer member;
    @Nullable public final LivingEntity attacker;
    public final LivingEntity target;
    public final DamageSource source;
    public final float amount;
    public final long now;
    public final boolean melee;
    public final boolean projectile;
    public final boolean magic;

    double percent;
    double flat;
    double reduction;

    Hit(ServerPlayer member, @Nullable LivingEntity attacker, LivingEntity target, DamageSource source, float amount, long now) {
        this.member = member;
        this.attacker = attacker;
        this.target = target;
        this.source = source;
        this.amount = amount;
        this.now = now;
        this.melee = attacker != null && Combat.isMelee(source, attacker);
        this.projectile = attacker != null && Combat.isProjectile(source, attacker);
        this.magic = Combat.isMagic(source);
    }

    /** Adds {@code fraction} (0.08 = 8%) to the outgoing multiplier. */
    public void addPercent(double fraction) {
        if (Double.isFinite(fraction) && fraction > 0) this.percent += fraction;
    }

    public void addFlat(double damage) {
        if (Double.isFinite(damage) && damage > 0) this.flat += damage;
    }

    /** Adds {@code fraction} (0.2 = 20%) to the incoming reduction. */
    public void reduce(double fraction) {
        if (Double.isFinite(fraction) && fraction > 0) this.reduction += fraction;
    }

    public double percent() {
        return percent;
    }

    float applyOutgoing(float current, float unscaled) {
        if (percent <= 0 && flat <= 0) return current;
        return (float) Math.max(0, current + flat + (unscaled + flat) * percent);
    }

    float applyIncoming(float base) {
        if (reduction <= 0) return base;
        return (float) Math.max(0, base * (1.0 - Math.min(0.9, reduction)));
    }
}
