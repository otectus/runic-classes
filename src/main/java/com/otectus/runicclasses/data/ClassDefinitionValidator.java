package com.otectus.runicclasses.data;

import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import io.github.edwinmindcraft.origins.api.OriginsAPI;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules a set of class definitions must satisfy before it goes live (docs/plan.md 2.2).
 *
 * <p>An {@link Severity#ERROR} aborts the commit and the previous snapshot stays live: a datapack edit
 * that breaks a class must not strip every member's grants on the next reload. Warnings are reported
 * and the data still commits.
 *
 * <p>The Origins checks (the class origin exists and the class layer lists it) are separate, because
 * Origins reloads its own dynamic registries in a listener this one cannot order itself against; they
 * run once the server has started and on {@code /runicclasses validate}.
 */
public final class ClassDefinitionValidator {

    public enum Severity { ERROR, WARNING }

    public record Problem(Severity severity, String message) {

        @Override
        public String toString() {
            return severity + " " + message;
        }
    }

    private ClassDefinitionValidator() {}

    public static boolean hasErrors(List<Problem> problems) {
        return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR);
    }

    public static List<Problem> validate(Map<ResourceLocation, ClassDefinition> definitions) {
        List<Problem> problems = new ArrayList<>();
        Map<String, String> perkClaims = new HashMap<>();
        Map<String, String> powerClaims = new HashMap<>();
        int skillCap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;

        for (ClassDefinition def : definitions.values()) {
            String who = def.id().toString();
            if (!ClassIds.MOD_ID.equals(def.id().getNamespace())) {
                error(problems, who + ": class ids live in the runicclasses namespace");
                continue;
            }
            if (def.starterBag() == null) error(problems, who + ": starter_bag is required");

            if (def.isClassless()) {
                if (def.primary() != null || def.secondary() != null || !def.perks().isEmpty() || def.powers() != null) {
                    error(problems, who + ": Classless must omit stats, perks and powers");
                }
                continue;
            }
            if (def.primary() == null || def.secondary() == null || def.powers() == null) {
                error(problems, who + ": only classless.json may omit stats, perks or powers");
                continue;
            }
            Skill primary = skill(def.primary());
            Skill secondary = skill(def.secondary());
            if (primary == null) error(problems, who + ": primary stat " + def.primary() + " is not a Runic Skills Aptitude");
            if (secondary == null) error(problems, who + ": secondary stat " + def.secondary() + " is not a Runic Skills Aptitude");
            if (primary != null && primary == secondary) error(problems, who + ": the two stats must differ");
            if (def.startLevel() < 1) error(problems, who + ": start_level must be at least 1");
            if (def.capBonus() < 0) error(problems, who + ": cap_bonus must not be negative");

            if (def.perks().size() != 6) error(problems, who + ": exactly 6 perks are required, found " + def.perks().size());
            Set<String> seen = new HashSet<>();
            for (ClassDefinition.PerkBinding binding : def.perks()) {
                String path = binding.path();
                if (!seen.add(path)) error(problems, who + ": perk " + binding.id() + " is listed twice");
                if (!ClassIds.MOD_ID.equals(binding.id().getNamespace())) {
                    error(problems, who + ": perk " + binding.id() + " must be in the runicclasses namespace");
                    continue;
                }
                Perk perk = RegistryPerks.PERKS_REGISTRY.get().getValue(binding.id());
                if (perk == null) {
                    error(problems, who + ": perk " + binding.id() + " is not registered");
                    continue;
                }
                if (!def.path().equals(RCPerks.ownerOf(path))) {
                    error(problems, who + ": perk " + binding.id() + " is registered for class " + RCPerks.ownerOf(path));
                }
                Skill gate = binding.gate() == ClassDefinition.Gate.PRIMARY ? primary : secondary;
                if (gate != null && perk.getSkill() != gate) {
                    error(problems, who + ": perk " + binding.id() + " belongs to " + perk.getSkill().getName()
                            + " but is gated on " + gate.getName());
                }
                int ceiling = skillCap + Math.max(0, def.capBonus());
                if (binding.level() < 1 || binding.level() > ceiling) {
                    error(problems, who + ": perk " + binding.id() + " level " + binding.level() + " is outside 1.." + ceiling);
                }
                ClassKit.PerkSpec spec = RCPerks.spec(path);
                if (spec != null) {
                    for (String key : binding.values().keySet()) {
                        if (spec.values().stream().noneMatch(v -> v.key().equals(key))) {
                            warn(problems, who + ": perk " + binding.id() + " has an unknown value key " + key);
                        }
                    }
                }
                String prior = perkClaims.putIfAbsent(path, who);
                if (prior != null && !prior.equals(who)) error(problems, "perk " + binding.id() + " is claimed by " + prior + " and " + who);
            }

            ClassDefinition.PowerSet set = def.powers();
            checkPower(problems, who, def, set.mark(), PowerTier.MARK, primary, powerClaims);
            checkPower(problems, who, def, set.seal(), PowerTier.SEAL, primary, powerClaims);
            checkPower(problems, who, def, set.crown(), PowerTier.CROWN, primary, powerClaims);
        }

        for (String path : ClassKit.CLASSES) {
            if (!definitions.containsKey(ClassIds.id(path))) warn(problems, "class " + path + " has no definition; its members get no grants");
        }
        if (!definitions.containsKey(ClassIds.CLASSLESS)) warn(problems, "classless has no definition; Classless players get no starter bag");
        return problems;
    }

    private static void checkPower(List<Problem> problems, String who, ClassDefinition def, ResourceLocation id,
                                   PowerTier tier, @Nullable Skill primary, Map<String, String> claims) {
        Power power = RegistryPowers.POWERS_REGISTRY.get().getValue(id);
        if (power == null) {
            error(problems, who + ": power " + id + " is not registered");
            return;
        }
        if (power.getTier() != tier) error(problems, who + ": power " + id + " is a " + power.getTier() + ", listed as " + tier);
        if (!ClassIds.id(def.path()).equals(power.getSchoolId())) {
            error(problems, who + ": power " + id + " has school " + power.getSchoolId() + ", expected " + ClassIds.id(def.path()));
        }
        if (primary != null && power.getGoverningSkill() != primary) {
            error(problems, who + ": power " + id + " is governed by " + power.getGoverningSkill().getName()
                    + ", not the primary stat " + primary.getName());
        }
        if (!def.path().equals(RCPowers.ownerOf(id.getPath()))) {
            error(problems, who + ": power " + id + " is registered for class " + RCPowers.ownerOf(id.getPath()));
        }
        String prior = claims.putIfAbsent(id.getPath(), who);
        if (prior != null && !prior.equals(who)) error(problems, "power " + id + " is claimed by " + prior + " and " + who);
    }

    /**
     * The Origins half: every class origin exists in the Origins registry and the class layer lists
     * it. Warnings only — a pack may deliberately drop a class from the layer.
     */
    public static List<Problem> validateOrigins(Map<ResourceLocation, ClassDefinition> definitions) {
        List<Problem> problems = new ArrayList<>();
        MappedRegistry<Origin> origins;
        MappedRegistry<OriginLayer> layers;
        try {
            origins = OriginsAPI.getOriginsRegistry();
            layers = OriginsAPI.getLayersRegistry();
        } catch (RuntimeException e) {
            warn(problems, "Origins registries are not available yet: " + e.getMessage());
            return problems;
        }
        OriginLayer layer = layers.get(ClassIds.CLASS_LAYER.location());
        if (layer == null) {
            warn(problems, "the class layer " + ClassIds.CLASS_LAYER.location() + " is not loaded; no one will be asked for a class");
            return problems;
        }
        for (ClassDefinition def : definitions.values()) {
            if (!origins.containsKey(def.origin())) warn(problems, def.id() + ": origin " + def.origin() + " is not loaded");
            else if (!layer.contains(def.origin())) warn(problems, def.id() + ": origin " + def.origin() + " is not in the class layer");
        }
        return problems;
    }

    @Nullable
    private static Skill skill(ResourceLocation id) {
        if (!"runicskills".equals(id.getNamespace())) return null;
        return RegistrySkills.getSkill(id.getPath());
    }

    private static void error(List<Problem> problems, String message) {
        problems.add(new Problem(Severity.ERROR, message));
    }

    private static void warn(List<Problem> problems, String message) {
        problems.add(new Problem(Severity.WARNING, message));
    }
}
