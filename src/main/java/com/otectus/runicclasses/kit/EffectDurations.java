package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.RunicClasses;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Rescales an effect instance's duration in place, before vanilla stores it.
 *
 * <p>{@code MobEffectEvent.Added} fires inside {@code LivingEntity.addEffect} before the new instance
 * is put in the active map or merged into an old one, so changing its duration there is exactly what
 * gets applied and synced. {@code MobEffectInstance} has no setter; the field is {@code duration},
 * SRG {@code f_19503_}, resolved through Forge's remapping helper so it works in dev and production.
 * Infinite durations are never touched.
 */
public final class EffectDurations {

    private static Field duration;
    private static boolean failed;

    private EffectDurations() {}

    /** Multiplies the remaining duration by {@code factor}, keeping at least one tick. */
    public static void scale(MobEffectInstance instance, double factor) {
        if (instance == null || instance.isInfiniteDuration() || !Double.isFinite(factor) || factor <= 0 || factor == 1.0) return;
        int current = instance.getDuration();
        int scaled = (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1, Math.round(current * factor)));
        set(instance, scaled);
    }

    private static void set(MobEffectInstance instance, int ticks) {
        if (failed) return;
        try {
            if (duration == null) duration = ObfuscationReflectionHelper.findField(MobEffectInstance.class, "f_19503_");
            duration.setInt(instance, ticks);
        } catch (RuntimeException | IllegalAccessException e) {
            failed = true;
            RunicClasses.LOGGER.warn("Effect durations cannot be adjusted ({}); duration Perks are inactive.", e.toString());
        }
    }
}
