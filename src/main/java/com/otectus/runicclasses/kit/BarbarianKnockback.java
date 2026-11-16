package com.otectus.runicclasses.kit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reckless Swing's extra knockback. {@code LivingKnockBackEvent} carries no attacker, so the hit marks
 * its target with the multiplier for the current tick, and the knockback that the same swing applies a
 * moment later picks it up.
 */
public final class BarbarianKnockback {

    private record Mark(long tick, float multiplier) {}

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    private BarbarianKnockback() {}

    public static void mark(LivingEntity target, long now, float multiplier) {
        MARKS.put(target.getUUID(), new Mark(now, multiplier));
        if (MARKS.size() > 512) MARKS.values().removeIf(m -> now - m.tick() > 20);
    }

    /** Both knockbacks of one sprinting swing (the hurt's own and the sprint shove) land in the same tick. */
    static void apply(LivingKnockBackEvent event) {
        Mark mark = MARKS.get(event.getEntity().getUUID());
        if (mark == null || mark.tick() != event.getEntity().level().getGameTime()) return;
        event.setStrength(event.getStrength() * mark.multiplier());
    }

    public static void clear() {
        MARKS.clear();
    }
}
