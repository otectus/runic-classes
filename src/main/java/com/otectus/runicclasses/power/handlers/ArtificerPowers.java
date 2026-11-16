package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** Artificer Powers. Mark: Spring Trap; Seal: Field Repair; Crown: Arc Capacitor. */
public final class ArtificerPowers implements ClassModule {

    static final String SPRING_TRAP = "cls_artificer_spring_trap";
    static final String FIELD_REPAIR = "cls_artificer_field_repair";
    static final String ARC_CAPACITOR = "cls_artificer_arc_capacitor";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee || !(hit.member.getMainHandItem().getItem() instanceof DiggerItem)) return;
        if (Powers.trigger(hit.member, SPRING_TRAP, hit.now, hit.target)) {
            hit.addPercent(Powers.value(SPRING_TRAP, "damage_bonus", 0.20));
        }
    }

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (!(hit.attacker instanceof ServerPlayer p) || !Powers.ready(p, ARC_CAPACITOR)) return;
        if (!(hit.melee || hit.projectile || hit.magic)) return;
        CombatMemory memory = CombatMemory.of(p);
        if (memory.increment("arc_capacitor") < Math.max(1, Powers.ticks(ARC_CAPACITOR, "every", 8))) return;
        memory.reset("arc_capacitor");
        if (!Powers.startCooldown(p, ARC_CAPACITOR, hit.now)) return;
        Powers.proc(p, ARC_CAPACITOR, hit.victim);
        float damage = (float) Powers.value(ARC_CAPACITOR, "damage", 5.0);
        int targets = Math.max(1, Powers.ticks(ARC_CAPACITOR, "targets", 3));
        double radius = Powers.value(ARC_CAPACITOR, "radius_blocks", 5.0);
        Deferred.later(() -> {
            List<LivingEntity> enemies = Combat.enemiesNear(p, p.position(), radius);
            enemies.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            for (LivingEntity enemy : enemies.subList(0, Math.min(targets, enemies.size()))) {
                Combat.secondaryHurt(p, enemy, p.damageSources().indirectMagic(p, p), damage);
            }
        });
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        ItemStack held = member.getMainHandItem();
        if (!held.isDamageableItem() || held.getMaxDamage() <= 0) return;
        double worn = (double) held.getDamageValue() / held.getMaxDamage();
        if (worn < 1.0 - Powers.value(FIELD_REPAIR, "durability_threshold", 0.10)) return;
        if (!Powers.trigger(member, FIELD_REPAIR, now, member)) return;
        int repair = (int) Math.ceil(held.getMaxDamage() * Powers.value(FIELD_REPAIR, "repair_fraction", 0.15));
        held.setDamageValue(Math.max(0, held.getDamageValue() - repair));
    }
}
