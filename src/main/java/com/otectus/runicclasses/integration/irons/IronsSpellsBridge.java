package com.otectus.runicclasses.integration.irons;

import com.otectus.runicclasses.RunicClasses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * Iron's Spells 'n Spellbooks, without its types. Class modules call these; when the mod is absent
 * every call is a no-op answering "not available" and the module takes its vanilla fallback
 * (docs/plan.md 2.8: every caster Perk and Power works without a magic mod). The typed half,
 * {@link IronsSpellsEvents}, is loaded by name only when the mod is present, so this class's constant
 * pool never names an Iron's Spells class.
 */
public final class IronsSpellsBridge {

    public static final String MOD_ID = "irons_spellbooks";

    /** The typed operations, installed by {@link IronsSpellsEvents#install()}. */
    public interface Impl {
        boolean addManaFraction(ServerPlayer player, double fraction);

        boolean refundCooldown(ServerPlayer player, String spellId, double fraction);

        @Nullable
        String spellIdOf(DamageSource source);
    }

    private static volatile Impl impl;
    private static Boolean loaded;

    private IronsSpellsBridge() {}

    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
            loaded = cached;
        }
        return cached;
    }

    /** Wires the typed listeners when Iron's Spells is installed. */
    public static void init() {
        if (!isLoaded()) return;
        try {
            Class.forName("com.otectus.runicclasses.integration.irons.IronsSpellsEvents").getMethod("install").invoke(null);
            RunicClasses.LOGGER.info("Iron's Spells found: caster class Perks and Powers use their spell branches.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            impl = null;
            RunicClasses.LOGGER.error("Iron's Spells is installed but its class bridge failed to load; caster classes use their vanilla fallbacks.", e);
        }
    }

    static void install(Impl implementation) {
        impl = implementation;
    }

    /** True when the spell branches are live (the mod is present and the bridge loaded). */
    public static boolean active() {
        return impl != null;
    }

    public static boolean addManaFraction(ServerPlayer player, double fraction) {
        Impl current = impl;
        return current != null && current.addManaFraction(player, fraction);
    }

    public static boolean refundCooldown(ServerPlayer player, String spellId, double fraction) {
        Impl current = impl;
        return current != null && current.refundCooldown(player, spellId, fraction);
    }

    @Nullable
    public static String spellIdOf(DamageSource source) {
        Impl current = impl;
        return current == null ? null : current.spellIdOf(source);
    }
}
