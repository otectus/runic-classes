package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Perk helpers: in-effect checks and numbers ({@link RCPerks}), and cooldowns kept in Runic Skills'
 * own perk-cooldown map, which it ticks down and saves, so a recharge survives relog and death.
 *
 * <p>Survive-lethal Perks also take Runic Skills' shared {@code runicskills.survive_lethal} lockout
 * (docs/plan.md 4): whichever rescue fires first locks every other for a minute, class or not.
 */
public final class Perks {

    public static final String SURVIVE_LOCKOUT = "runicskills.survive_lethal";
    public static final int SURVIVE_LOCKOUT_TICKS = 1200;

    private Perks() {}

    public static boolean on(Player player, String path) {
        return RCPerks.on(player, path);
    }

    public static double v(String path, int index) {
        return RCPerks.value(path, index);
    }

    /** The value as a fraction: a stored 8 (percent) reads 0.08. */
    public static double pct(String path, int index) {
        return RCPerks.value(path, index) / 100.0;
    }

    /** Seconds stored in the class data, as ticks. */
    public static int ticks(String path, int index) {
        return (int) Math.round(RCPerks.value(path, index) * 20.0);
    }

    public static boolean ready(Player player, String path) {
        SkillCapability cap = SkillCapability.get(player);
        Perk perk = RCPerks.get(path);
        return cap != null && perk != null && cap.getCooldown(perk) <= 0;
    }

    public static void startCooldown(Player player, String path, int ticks) {
        SkillCapability cap = SkillCapability.get(player);
        Perk perk = RCPerks.get(path);
        if (cap != null && perk != null && ticks > 0) cap.setCooldown(perk, ticks);
    }

    public static boolean surviveLocked(Player player) {
        SkillCapability cap = SkillCapability.get(player);
        return cap == null || cap.getCooldown(SURVIVE_LOCKOUT) > 0;
    }

    public static void lockSurvive(Player player) {
        SkillCapability cap = SkillCapability.get(player);
        if (cap != null) cap.setCooldown(SURVIVE_LOCKOUT, SURVIVE_LOCKOUT_TICKS);
        if (player instanceof ServerPlayer serverPlayer) SyncSkillCapabilityCP.send(serverPlayer);
    }

    /** Damage that no survival Perk may cheat: the void, /kill and the like. */
    public static boolean unsurvivable(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
