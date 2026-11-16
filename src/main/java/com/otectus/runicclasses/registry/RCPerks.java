package com.otectus.runicclasses.registry;

import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.data.ClassKit;
import com.otectus.runicskills.client.core.Value;
import com.otectus.runicskills.client.core.ValueType;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The 78 class Perks, registered into Runic Skills' own {@code runicskills:perks} registry under the
 * {@code runicclasses} namespace, so they appear in the Skills screen, use the toggle packet, persist in
 * the skill capability and count toward any perk budget exactly like Runic Skills' own.
 *
 * <p>Runic Skills keys several caches by path alone, so every path carries the {@code cls_<class>_}
 * prefix and is globally unique. The owner map is fixed by this registration, not by data: it is what
 * the visibility hook consults on both sides, including a client that never loads server data.
 */
public final class RCPerks {

    public static final DeferredRegister<Perk> PERKS = DeferredRegister.create(RegistryPerks.PERKS_KEY, ClassIds.MOD_ID);

    private static final Map<String, RegistryObject<Perk>> BY_PATH = new LinkedHashMap<>();
    private static final Map<String, ClassKit.PerkSpec> SPECS = new HashMap<>();
    private static final Map<String, String> OWNER = new HashMap<>();

    static {
        for (ClassKit.PerkSpec spec : ClassKit.PERKS) {
            SPECS.put(spec.path(), spec);
            OWNER.put(spec.path(), spec.classId());
            BY_PATH.put(spec.path(), PERKS.register(spec.path(), () -> build(spec, spec.level(), Map.of())));
        }
    }

    private RCPerks() {}

    public static void register(IEventBus modBus) {
        PERKS.register(modBus);
    }

    private static Perk build(ClassKit.PerkSpec spec, int level, Map<String, Double> overrides) {
        Value[] values = new Value[spec.values().size()];
        for (int i = 0; i < values.length; i++) {
            ClassKit.ValueSpec v = spec.values().get(i);
            values[i] = new Value(ValueType.valueOf(v.type()), overrides.getOrDefault(v.key(), v.value()));
        }
        return new Perk(ClassIds.id(spec.path()), skill(spec.skill()), level,
                new ResourceLocation(ClassIds.MOD_ID, "textures/perk/" + spec.path() + ".png"), values);
    }

    /** The registry object for a Runic Skills Aptitude by name; a {@code Supplier<Skill>} as the constructors want. */
    public static Supplier<Skill> skill(String name) {
        return switch (name) {
            case "strength" -> RegistrySkills.STRENGTH;
            case "constitution" -> RegistrySkills.CONSTITUTION;
            case "dexterity" -> RegistrySkills.DEXTERITY;
            case "endurance" -> RegistrySkills.ENDURANCE;
            case "intelligence" -> RegistrySkills.INTELLIGENCE;
            case "building" -> RegistrySkills.BUILDING;
            case "wisdom" -> RegistrySkills.WISDOM;
            case "magic" -> RegistrySkills.MAGIC;
            case "fortune" -> RegistrySkills.FORTUNE;
            case "tinkering" -> RegistrySkills.TINKERING;
            default -> throw new IllegalArgumentException("unknown Runic Skills Aptitude " + name);
        };
    }

    /** The class a class Perk belongs to ({@code fighter}), or {@code null} for every other perk. */
    @Nullable
    public static String ownerOf(String perkPath) {
        return OWNER.get(perkPath);
    }

    @Nullable
    public static ClassKit.PerkSpec spec(String path) {
        return SPECS.get(path);
    }

    @Nullable
    public static Perk get(String path) {
        RegistryObject<Perk> object = BY_PATH.get(path);
        return object != null && object.isPresent() ? object.get() : null;
    }

    /** Whether this class Perk is in effect for the player: enabled, its level met, and the player of its class. */
    public static boolean on(@Nullable Player player, String path) {
        Perk perk = get(path);
        return perk != null && player != null && perk.isEnabled(player);
    }

    /** The perk's {@code index}th number as currently adopted (class JSON, else the registered default). */
    public static double value(String path, int index) {
        Perk perk = get(path);
        if (perk == null) return 0.0;
        double[] values = perk.getValue();
        return index >= 0 && index < values.length ? values[index] : 0.0;
    }

    public static Map<String, RegistryObject<Perk>> all() {
        return Collections.unmodifiableMap(BY_PATH);
    }

    /**
     * Adopts a definition's gate levels and numbers into the registered Perks, in place, the same way
     * Runic Skills refreshes its own from config. Called when the server commits class data and when
     * a client receives it, so the Skills screen shows the server's numbers.
     */
    public static void applyTunables(ClassDefinition definition) {
        for (ClassDefinition.PerkBinding binding : definition.perks()) {
            ClassKit.PerkSpec spec = SPECS.get(binding.path());
            Perk perk = get(binding.path());
            if (spec == null || perk == null) continue;
            perk.adoptTunables(build(spec, binding.level(), binding.values()));
        }
    }
}
