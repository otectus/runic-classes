package com.otectus.runicclasses.integration.runicskills;

import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.config.RCServerConfig;
import com.otectus.runicclasses.kit.KitDispatcher;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.service.ClassHelper;
import com.otectus.runicclasses.service.ClassReconciler;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.SkillCapHooks;
import com.otectus.runicskills.event.RespecEvent;
import com.otectus.runicskills.registry.ContentGateHooks;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

import java.util.function.BiPredicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * Installs RunicClasses into Runic Skills' 2.4.0 hooks at mod construction, on both sides.
 *
 * <p>Runic Skills never names this mod: every behaviour change on its side is a volatile hook with a
 * no-op default (docs/plan.md 1.5). Each hook here chains to whatever was installed before it, so a
 * second addon using the same hook keeps working. Every answer reads the <em>applied</em> record
 * ({@link ClassState}), never the class definitions, so the arithmetic can never count a grant that
 * was not made, and the client answers from the state the server synced to it.
 */
public final class RunicSkillsHooksInstaller {

    private RunicSkillsHooksInstaller() {}

    public static void install() {
        ToIntBiFunction<Player, Skill> previousCap = SkillCapHooks.skillCapBonus;
        SkillCapHooks.skillCapBonus = (player, skill) -> previousCap.applyAsInt(player, skill) + capBonus(player, skill);

        ToIntFunction<Player> previousGlobal = SkillCapHooks.globalCapBonus;
        SkillCapHooks.globalCapBonus = player -> previousGlobal.applyAsInt(player)
                + (RCServerConfig.get(RCServerConfig.CLASS_FREE_LEVELS_EXTEND_GLOBAL_CAP, true) ? freeLevels(player) : 0);

        ToIntFunction<Player> previousEarned = SkillCapHooks.earnedLevelAdjust;
        SkillCapHooks.earnedLevelAdjust = player -> previousEarned.applyAsInt(player)
                + (RCServerConfig.get(RCServerConfig.CLASS_FREE_LEVELS_COUNT_AS_EARNED, false) ? 0 : freeLevels(player));

        BiPredicate<Player, Perk> previousPerk = ContentGateHooks.perkAvailable;
        ContentGateHooks.perkAvailable = (player, perk) -> perkAvailable(player, perk) && previousPerk.test(player, perk);

        BiPredicate<Player, Power> previousPower = ContentGateHooks.powerAvailable;
        ContentGateHooks.powerAvailable = (player, power) -> powerAvailable(player, power) && previousPower.test(player, power);

        ToDoubleBiFunction<Player, Skill> previousDiscount = ProgressionHooks.levelUpDiscount;
        ProgressionHooks.levelUpDiscount = (player, skill) -> previousDiscount.applyAsDouble(player, skill)
                + KitDispatcher.levelUpDiscount(player, skill);

        MinecraftForge.EVENT_BUS.addListener((RespecEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) ClassReconciler.onRespec(player);
        });
        RunicClasses.LOGGER.debug("Installed the Runic Skills class hooks.");
    }

    /** +cap on the two applied class stats; 0 everywhere else. */
    static int capBonus(Player player, Skill skill) {
        ClassState state = RCCapabilities.get(player);
        if (state == null || !state.hasAppliedClass() || skill == null) return 0;
        String name = skill.getName();
        return name.equals(state.appliedPrimary) || name.equals(state.appliedSecondary) ? state.appliedCapBonus : 0;
    }

    static int freeLevels(Player player) {
        ClassState state = RCCapabilities.get(player);
        return state == null || !state.hasAppliedClass() ? 0 : state.freeLevels();
    }

    /** One hash lookup for every non-class perk (the hot path); a class perk asks for the applied class. */
    static boolean perkAvailable(Player player, Perk perk) {
        String owner = RCPerks.ownerOf(perk.getName());
        return owner == null || owner.equals(ClassHelper.appliedClass(player));
    }

    static boolean powerAvailable(Player player, Power power) {
        String owner = RCPowers.ownerOf(power.getName());
        return owner == null || owner.equals(ClassHelper.appliedClass(player));
    }
}
