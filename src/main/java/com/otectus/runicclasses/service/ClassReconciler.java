package com.otectus.runicclasses.service;

import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.config.RCCommonConfig;
import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.kit.PerkAttributes;
import com.otectus.runicclasses.network.RCNetwork;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.progression.SkillCapHooks;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps what a player has been granted in step with the class they have chosen (docs/plan.md 2.5).
 *
 * <p>Origins posts no event when a layer changes, so this compares the class layer against the
 * applied record at login, respawn, clone, every ten ticks, and whenever RunicClasses itself changes
 * the layer. Re-picking is a wholesale swap: undo every grant of the old class with the numbers it was
 * made with, then apply the new class. Undo runs before apply and clears the applied record first, so
 * the cap hook has already dropped the raise when the old stats are clamped back to the base cap.
 *
 * <p>Every level change goes through {@link ProgressionService} with {@link ProgressionService.Cause#CLASS},
 * so it fires the same events, reconciles attributes and titles, and syncs like any other change.
 */
public final class ClassReconciler {

    /** UUIDs whose unknown class layer value was already reported, so the log says it once. */
    private static final Set<UUID> REPORTED_UNKNOWN = ConcurrentHashMap.newKeySet();

    private ClassReconciler() {}

    /** Runs a full reconcile: diff, swap if needed, repair if not, then the one-time bag. */
    public static void reconcile(ServerPlayer player) {
        ClassState state = RCCapabilities.get(player);
        SkillCapability skills = SkillCapability.get(player);
        if (state == null || skills == null) return;
        ClassHelper.Selection selection = ClassHelper.selection(player);
        switch (selection.kind()) {
            case NO_CONTAINER -> {
                return;
            }
            case UNKNOWN -> {
                // A class origin with no definition (a datapack removed it, or the snapshot was
                // rejected): change nothing, so a data mistake never strips a player's grants.
                if (REPORTED_UNKNOWN.add(player.getUUID())) {
                    RunicClasses.LOGGER.warn("{} holds class origin {} with no class definition; leaving their grants as they are.",
                            player.getGameProfile().getName(), selection.origin());
                }
                return;
            }
            default -> REPORTED_UNKNOWN.remove(player.getUUID());
        }

        ClassDefinition target = selection.kind() == ClassHelper.Kind.CLASS ? selection.definition() : null;
        boolean changed = false;
        if (target == null) {
            if (state.hasAppliedClass()) {
                undo(player, state, skills);
                changed = true;
            }
        } else if (!matches(state, target)) {
            if (state.hasAppliedClass()) undo(player, state, skills);
            apply(player, state, skills, target);
            changed = true;
        } else {
            changed = ensure(player, state, skills, target, false);
        }

        if (selection.isChosen()) {
            changed |= StarterBagService.maybeGrant(player, state, selection);
        }
        if (changed) finish(player, state);
    }

    /** True when the applied record is exactly what {@code def} would grant now. */
    public static boolean matches(ClassState state, ClassDefinition def) {
        if (!def.id().toString().equals(state.appliedClass)) return false;
        if (def.primary() == null || def.secondary() == null) return false;
        if (!def.primary().getPath().equals(state.appliedPrimary)) return false;
        if (!def.secondary().getPath().equals(state.appliedSecondary)) return false;
        if (def.startLevel() != state.appliedStartLevel || def.capBonus() != state.appliedCapBonus) return false;
        return perkPaths(def).equals(state.appliedPerks) && powerPaths(def).equals(state.appliedPowers);
    }

    /**
     * Grants {@code def}: both stats raised to the start level (recording what was actually given),
     * the starting Perks enabled, the Perk and Power lists recorded. Powers are not equipped: slots
     * and Power Points stay the player's budget (OQ-5).
     */
    static void apply(ServerPlayer player, ClassState state, SkillCapability skills, ClassDefinition def) {
        Skill primary = skill(def.primary());
        Skill secondary = skill(def.secondary());
        if (primary == null || secondary == null) return;
        state.appliedClass = def.id().toString();
        state.appliedPrimary = primary.getName();
        state.appliedSecondary = secondary.getName();
        state.appliedStartLevel = def.startLevel();
        state.appliedCapBonus = def.capBonus();
        state.grantPrimary = 0;
        state.grantSecondary = 0;
        state.appliedPerks.clear();
        state.appliedPerks.addAll(perkPaths(def));
        state.appliedPowers.clear();
        state.appliedPowers.addAll(powerPaths(def));
        ClassHelper.invalidate(player.getUUID());

        state.grantPrimary = raise(player, skills, primary, def.startLevel());
        state.grantSecondary = raise(player, skills, secondary, def.startLevel());
        enableStartingPerks(player, skills, def);
        log("{} became {} (+{} {}, +{} {})", player.getGameProfile().getName(), def.id(),
                state.grantPrimary, primary.getName(), state.grantSecondary, secondary.getName());
        player.displayClientMessage(Component.translatable("message.runicclasses.class.applied",
                Component.translatable("origin." + def.id().getNamespace() + "." + def.path() + ".name")), true);
    }

    /**
     * Removes every grant of the applied class: the free levels leave (purchased ones stay), a stat
     * above the base cap returns to it (OQ-3: accepted, no refund), the class Perks drop to rank 0,
     * the class Powers leave their slots and their cooldowns and windows are cleared.
     */
    static void undo(ServerPlayer player, ClassState state, SkillCapability skills) {
        String old = state.appliedClass;
        String primaryName = state.appliedPrimary;
        String secondaryName = state.appliedSecondary;
        int grantPrimary = state.grantPrimary;
        int grantSecondary = state.grantSecondary;
        List<String> perks = new ArrayList<>(state.appliedPerks);
        List<String> powers = new ArrayList<>(state.appliedPowers);
        // Clear first: from here on the cap hook reports the base cap, which is what the clamp needs.
        state.clearApplied();
        ClassHelper.invalidate(player.getUUID());

        lower(player, skills, RegistrySkills.getSkill(primaryName), grantPrimary);
        lower(player, skills, RegistrySkills.getSkill(secondaryName), grantSecondary);
        for (String path : perks) {
            Perk perk = RCPerks.get(path);
            if (perk != null && skills.getPerkRank(perk) > 0) skills.setPerkRank(perk, 0);
        }
        for (String path : powers) {
            skills.unequipUnknownPower(path);
            skills.powerCooldowns.remove(path);
            skills.powerWindows.remove(path);
        }
        log("{} left {} (-{} {}, -{} {})", player.getGameProfile().getName(), old,
                grantPrimary, primaryName, grantSecondary, secondaryName);
        player.displayClientMessage(Component.translatable("message.runicclasses.class.cleared"), true);
    }

    /**
     * Same class, periodic repair. A class stat below the start level (an operator lowered it, or a
     * respec that the event did not reach) is raised back and the grant grows by what was given, never
     * past {@code startLevel - 1}. Starting Perks are enabled again only after a respec: otherwise the
     * player's own toggles are left alone ("active by default", not forced on).
     */
    static boolean ensure(ServerPlayer player, ClassState state, SkillCapability skills, ClassDefinition def,
                          boolean afterRespec) {
        Skill primary = skill(def.primary());
        Skill secondary = skill(def.secondary());
        if (primary == null || secondary == null) return false;
        int cap = Math.max(0, def.startLevel() - 1);
        int beforeP = state.grantPrimary;
        int beforeS = state.grantSecondary;
        state.grantPrimary = Math.min(cap, state.grantPrimary + raise(player, skills, primary, def.startLevel()));
        state.grantSecondary = Math.min(cap, state.grantSecondary + raise(player, skills, secondary, def.startLevel()));
        // Undo must never take a stat below 1, whatever happened to it since the grant.
        state.grantPrimary = Math.min(state.grantPrimary, Math.max(0, skills.getSkillLevel(primary) - 1));
        state.grantSecondary = Math.min(state.grantSecondary, Math.max(0, skills.getSkillLevel(secondary) - 1));
        boolean changed = beforeP != state.grantPrimary || beforeS != state.grantSecondary;
        if (afterRespec) changed |= enableStartingPerks(player, skills, def);
        return changed;
    }

    /**
     * {@code /respec} reset every Aptitude to 1 and every perk to rank 0: the old grants are gone with
     * the levels they sat on, so they are zeroed and the class is granted again.
     */
    public static void onRespec(ServerPlayer player) {
        ClassState state = RCCapabilities.get(player);
        SkillCapability skills = SkillCapability.get(player);
        if (state == null || skills == null || !state.hasAppliedClass()) return;
        ClassDefinition def = ClassHelper.selection(player).definition();
        if (def == null || def.isClassless() || !matches(state, def)) {
            // The layer and the record disagree; the regular reconcile sorts that out.
            reconcile(player);
            return;
        }
        state.grantPrimary = 0;
        state.grantSecondary = 0;
        ensure(player, state, skills, def, true);
        finish(player, state);
    }

    private static int raise(ServerPlayer player, SkillCapability skills, Skill skill, int startLevel) {
        int before = skills.getSkillLevel(skill);
        if (before >= startLevel) return 0;
        ProgressionService.Outcome outcome =
                ProgressionService.setSkillLevel(player, skill, startLevel, ProgressionService.Cause.CLASS);
        if (!outcome.changed()) {
            log("class grant of {} {} for {} was refused: {}", skill.getName(), startLevel,
                    player.getGameProfile().getName(), outcome.denial());
            return 0;
        }
        return Math.max(0, outcome.current() - before);
    }

    private static void lower(ServerPlayer player, SkillCapability skills, @Nullable Skill skill, int grant) {
        if (skill == null) return;
        int level = skills.getSkillLevel(skill);
        int target = Math.max(1, level - Math.max(0, grant));
        target = Math.min(target, SkillCapHooks.skillCap(player, skill));
        if (target != level) ProgressionService.setSkillLevel(player, skill, target, ProgressionService.Cause.CLASS);
    }

    /**
     * Enables the starting Perks that are at rank 0. No exemptions (OQ-6): a pack that caps active perks
     * counts them, and one that would put the player over the cap is left for the player to enable.
     */
    private static boolean enableStartingPerks(ServerPlayer player, SkillCapability skills, ClassDefinition def) {
        boolean changed = false;
        for (ClassDefinition.PerkBinding binding : def.perks()) {
            if (!binding.starting()) continue;
            Perk perk = RCPerks.get(binding.path());
            if (perk == null || skills.getPerkRank(perk) > 0) continue;
            int cap = RegistryPerks.effectivePerkCap(skills);
            if (cap > 0 && RegistryPerks.countEnabledPerks(skills) >= cap) continue;
            skills.setPerkRank(perk, 1);
            changed = true;
        }
        return changed;
    }

    /** One reconciliation of everything downstream of a grant change, then both syncs. */
    private static void finish(ServerPlayer player, ClassState state) {
        ClassHelper.invalidate(player.getUUID());
        RegistryAttributes.modifierAttributes(player);
        RegistryTitles.syncTitles(player);
        PerkAttributes.refresh(player);
        SyncSkillCapabilityCP.send(player);
        RCNetwork.syncState(player);
    }

    @Nullable
    private static Skill skill(@Nullable ResourceLocation id) {
        return id == null ? null : RegistrySkills.getSkill(id.getPath());
    }

    private static List<String> perkPaths(ClassDefinition def) {
        List<String> out = new ArrayList<>();
        for (ClassDefinition.PerkBinding binding : def.perks()) out.add(binding.path());
        return out;
    }

    private static List<String> powerPaths(ClassDefinition def) {
        List<String> out = new ArrayList<>();
        if (def.powers() != null) for (ResourceLocation id : def.powers().all()) out.add(id.getPath());
        return out;
    }

    static void log(String message, Object... args) {
        if (RCCommonConfig.debug()) RunicClasses.LOGGER.info(message, args);
        else RunicClasses.LOGGER.debug(message, args);
    }

    public static void forget(UUID player) {
        REPORTED_UNKNOWN.remove(player);
    }
}
