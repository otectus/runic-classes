package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Ranger Powers. Mark: Second Arrow; Seal: Pin; Crown: Volley of the Watch. */
public final class RangerPowers implements ClassModule {

    static final String SECOND_ARROW = "cls_ranger_second_arrow";
    static final String PIN = "cls_ranger_pin";
    static final String VOLLEY = "cls_ranger_volley_of_the_watch";
    /** Marks the split arrows a volley spawns, so they never arm or split again. */
    private static final String VOLLEY_CHILD = "runicclasses_volley_child";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.projectile) return;
        ServerPlayer p = hit.member;
        if (hit.source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()
                && Powers.trigger(p, PIN, hit.now, hit.target)) {
            hit.target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Powers.ticks(PIN, "duration_ticks", 40), 4), p);
        }
        if (hit.source.getDirectEntity() != null && hit.source.getDirectEntity().getPersistentData().getBoolean(VOLLEY_CHILD)) return;
        CombatMemory memory = CombatMemory.of(p);
        int hits = memory.recordWithin("volley_hits", hit.now, Powers.ticks(VOLLEY, "window_ticks", 80));
        if (hits >= Math.max(1, Powers.ticks(VOLLEY, "hits", 3)) && Powers.ready(p, VOLLEY) && Powers.startCooldown(p, VOLLEY, hit.now)) {
            Powers.proc(p, VOLLEY, p);
            memory.clearWithin("volley_hits");
            memory.open("volley_armed", hit.now + 20 * 30);
        }
    }

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!hit.projectile || !(hit.attacker instanceof ServerPlayer p)) return;
        if (p.distanceTo(hit.victim) < Powers.value(SECOND_ARROW, "min_distance_blocks", 12.0)) return;
        if (!Powers.trigger(p, SECOND_ARROW, hit.now, hit.victim)) return;
        float echo = (float) (hit.amount() * Powers.value(SECOND_ARROW, "damage_fraction", 0.5));
        LivingEntity target = hit.victim;
        var source = hit.source;
        Deferred.later(() -> Combat.secondaryHurt(p, target, source, echo));
    }

    @Override
    public void projectileSpawned(ServerPlayer member, Projectile projectile) {
        if (!(projectile instanceof AbstractArrow arrow) || arrow.getPersistentData().getBoolean(VOLLEY_CHILD)) return;
        if (!CombatMemory.of(member).consume("volley_armed", member.level().getGameTime())) return;
        double spread = Math.toRadians(Powers.value(VOLLEY, "spread_degrees", 10.0));
        // Spawned after this tick's entity processing: adding entities inside a join event is unsafe.
        Deferred.later(() -> {
            if (!(member.level() instanceof ServerLevel level) || arrow.isRemoved()) return;
            for (double offset : new double[]{-spread, spread}) {
                Arrow copy = new Arrow(level, member);
                copy.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                Vec3 velocity = arrow.getDeltaMovement().yRot((float) offset);
                copy.setDeltaMovement(velocity);
                copy.setYRot((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
                copy.setXRot((float) Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())));
                copy.setBaseDamage(arrow.getBaseDamage());
                copy.setCritArrow(arrow.isCritArrow());
                copy.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                copy.getPersistentData().putBoolean(VOLLEY_CHILD, true);
                level.addFreshEntity(copy);
            }
        });
    }
}
