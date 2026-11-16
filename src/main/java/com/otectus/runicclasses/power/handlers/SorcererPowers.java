package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import com.otectus.runicclasses.kit.SpellCast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import javax.annotation.Nullable;

/** Sorcerer Powers. Mark: Sparkvein; Seal: Cascade; Crown: Arcane Upwelling. */
public final class SorcererPowers implements ClassModule {

    static final String SPARKVEIN = "cls_sorcerer_sparkvein";
    static final String CASCADE = "cls_sorcerer_cascade";
    static final String ARCANE_UPWELLING = "cls_sorcerer_arcane_upwelling";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.magic) return;
        ServerPlayer p = hit.member;
        if (hit.target.getHealth() >= hit.target.getMaxHealth() && Powers.trigger(p, SPARKVEIN, hit.now, hit.target)) {
            hit.addPercent(Powers.value(SPARKVEIN, "damage_bonus", 0.15));
        }
        // Without spells, Arcane Upwelling counts magic hits instead of casts.
        if (!IronsSpellsBridge.active() && Powers.ready(p, ARCANE_UPWELLING) && countSurge(p)) surge(p, hit.target, hit.now);
    }

    @Override
    public void spellCast(ServerPlayer member, SpellCast cast) {
        if (Powers.ready(member, ARCANE_UPWELLING) && countSurge(member)) {
            surge(member, lookTarget(member), member.level().getGameTime());
        }
    }

    private static boolean countSurge(ServerPlayer p) {
        CombatMemory memory = CombatMemory.of(p);
        if (memory.increment("arcane_upwelling") < Math.max(1, Powers.ticks(ARCANE_UPWELLING, "every", 6))) return false;
        memory.reset("arcane_upwelling");
        return true;
    }

    private static void surge(ServerPlayer p, @Nullable LivingEntity target, long now) {
        if (!Powers.startCooldown(p, ARCANE_UPWELLING, now)) return;
        Powers.proc(p, ARCANE_UPWELLING, target);
        int roll = target == null ? 1 + p.getRandom().nextInt(2) : p.getRandom().nextInt(3);
        float damage = (float) Powers.value(ARCANE_UPWELLING, "lightning_damage", 6.0);
        float heal = (float) Powers.value(ARCANE_UPWELLING, "heal", 4.0);
        double radius = Powers.value(ARCANE_UPWELLING, "radius_blocks", 4.0);
        Deferred.later(() -> {
            if (!p.isAlive()) return;
            switch (roll) {
                case 0 -> {
                    if (target == null || !target.isAlive() || !(p.level() instanceof ServerLevel level)) return;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.getX(), target.getY(), target.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                    Combat.secondaryHurt(p, target, p.damageSources().indirectMagic(p, p), damage);
                }
                case 1 -> p.heal(heal);
                default -> {
                    for (LivingEntity enemy : Combat.enemiesNear(p, p.position(), radius)) {
                        enemy.knockback(1.2, p.getX() - enemy.getX(), p.getZ() - enemy.getZ());
                    }
                }
            }
        });
    }

    /** What the Sorcerer is looking at within 24 blocks, else the nearest enemy within 8. */
    @Nullable
    private static LivingEntity lookTarget(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 reach = eye.add(p.getViewVector(1.0F).scale(24.0));
        AABB box = p.getBoundingBox().expandTowards(p.getViewVector(1.0F).scale(24.0)).inflate(1.0);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(p, eye, reach, box,
                e -> e instanceof LivingEntity && Combat.isEnemy(p, e), 24.0 * 24.0);
        if (result != null && result.getEntity() instanceof LivingEntity living) return living;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity enemy : Combat.enemiesNear(p, p.position(), 8.0)) {
            double d = enemy.distanceToSqr(p);
            if (d < best) {
                best = d;
                nearest = enemy;
            }
        }
        return nearest;
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (!Combat.isMagic(source) || !Powers.trigger(member, CASCADE, member.level().getGameTime(), member)) return;
        if (!IronsSpellsBridge.addManaFraction(member, Powers.value(CASCADE, "mana_fraction", 0.20))) {
            member.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Powers.ticks(CASCADE, "duration_ticks", 80), 1));
        }
    }
}
