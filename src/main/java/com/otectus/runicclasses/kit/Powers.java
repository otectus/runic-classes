package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * Class Powers through Runic Skills' own dispatch helpers, like every Runic Skills Power:
 * {@link PowerDispatch#isEquipped} re-runs the full eligibility check (gates, prerequisite chain,
 * Power Point budget, class restriction) on every proc, the internal cooldown is Runic Skills'
 * persisted cooldown debt, and a proc fires the shared presentation and {@code PowerProcEvent}.
 * Magnitudes read Runic Skills' Power override datapack ({@code data/runicclasses/powers/<id>.json},
 * {@code values}) with the documented defaults as fallback.
 */
public final class Powers {

    private Powers() {}

    /** Equipped and currently eligible. */
    public static boolean ready(@Nullable Player player, String path) {
        RegistryObject<Power> object = RCPowers.object(path);
        return player != null && object != null && PowerDispatch.isEquipped(player, object);
    }

    /** Starts the internal cooldown if it is not running; true when the effect should go ahead. */
    public static boolean startCooldown(ServerPlayer player, String path, long now) {
        Power power = RCPowers.get(path);
        return power != null && PowerDispatch.checkAndStartCooldown(player, power, now);
    }

    public static void proc(ServerPlayer player, String path, @Nullable Entity target) {
        Power power = RCPowers.get(path);
        if (power != null) PowerDispatch.fireProc(player, power, target);
    }

    /** Ready, off cooldown (now started) and proc presented: the whole trigger in one call. */
    public static boolean trigger(ServerPlayer player, String path, long now, @Nullable Entity target) {
        if (!ready(player, path) || !startCooldown(player, path, now)) return false;
        proc(player, path, target);
        return true;
    }

    public static double value(String path, String key, double fallback) {
        Power power = RCPowers.get(path);
        return power == null ? fallback : PowerOverridesManager.valueOr(power, key, fallback);
    }

    public static int ticks(String path, String key, int fallback) {
        Power power = RCPowers.get(path);
        return power == null ? fallback : PowerOverridesManager.intValueOr(power, key, fallback);
    }
}
