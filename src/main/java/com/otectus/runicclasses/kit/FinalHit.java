package com.otectus.runicclasses.kit;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * One hit after armour and resistance ({@code LivingDamageEvent}), before absorption is spent and
 * health is lowered. {@link #healthAfter} is the health the victim will be left with if nothing
 * changes the amount, which is what every "drops below" threshold compares against.
 */
public final class FinalHit {

    @Nullable public final LivingEntity attacker;
    public final LivingEntity victim;
    public final DamageSource source;
    public final long now;
    public final float healthBefore;
    public final float maxHealth;
    public final boolean melee;
    public final boolean projectile;
    public final boolean magic;
    float amount;

    FinalHit(@Nullable LivingEntity attacker, LivingEntity victim, DamageSource source, float amount, long now) {
        this.attacker = attacker;
        this.victim = victim;
        this.source = source;
        this.amount = amount;
        this.now = now;
        this.healthBefore = victim.getHealth();
        this.maxHealth = Math.max(1.0f, victim.getMaxHealth());
        this.melee = attacker != null && Combat.isMelee(source, attacker);
        this.projectile = attacker != null && Combat.isProjectile(source, attacker);
        this.magic = Combat.isMagic(source);
    }

    public float amount() {
        return amount;
    }

    /** Negates or lowers the hit. Never raises it. */
    public void setAmount(float lower) {
        if (Float.isFinite(lower) && lower < amount) amount = Math.max(0, lower);
    }

    /** Health left once absorption has taken its share. */
    public float healthAfter() {
        return healthBefore - Math.max(0, amount - victim.getAbsorptionAmount());
    }

    /** The hit as a share of the victim's maximum health. */
    public float fraction() {
        return amount / maxHealth;
    }

    /** True when this hit takes the victim from above {@code share} of max health to at or below it. */
    public boolean crossesBelow(double share) {
        double line = share * maxHealth;
        return healthBefore > line && healthAfter() <= line && healthAfter() > 0;
    }

    /** True when the victim is left at or below {@code share} of max health (and alive). */
    public boolean leavesAtOrBelow(double share) {
        float after = healthAfter();
        return after > 0 && after <= share * maxHealth;
    }
}
