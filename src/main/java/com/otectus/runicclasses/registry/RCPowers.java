package com.otectus.runicclasses.registry;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.data.ClassKit;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 39 class Powers: one Runic Mark, Seal and Crown per class, registered into
 * {@code runicskills:powers}.
 *
 * <p><b>School id = the class</b> ({@code runicclasses:fighter}). With one Power per tier sharing it,
 * Runic Skills' prerequisite chain gives the intended ladder for free: the class Seal needs the class
 * Mark equipped and the class Crown needs the class Seal. Gates are Runic Skills' own tier gates on the
 * class's primary Aptitude, and every other Runic Skills rule applies unchanged (owner decision OQ-7).
 */
public final class RCPowers {

    public static final DeferredRegister<Power> POWERS = DeferredRegister.create(RegistryPowers.POWERS_KEY, ClassIds.MOD_ID);

    private static final Map<String, RegistryObject<Power>> BY_PATH = new LinkedHashMap<>();
    private static final Map<String, ClassKit.PowerSpec> SPECS = new HashMap<>();
    private static final Map<String, String> OWNER = new HashMap<>();

    /** The tier gates last adopted, so a Runic Skills config change is noticed cheaply. */
    private static volatile int[] adoptedGates = new int[0];

    static {
        for (ClassKit.PowerSpec spec : ClassKit.POWERS) {
            SPECS.put(spec.path(), spec);
            OWNER.put(spec.path(), spec.classId());
            BY_PATH.put(spec.path(), POWERS.register(spec.path(), () -> build(spec)));
        }
    }

    private RCPowers() {}

    public static void register(IEventBus modBus) {
        POWERS.register(modBus);
    }

    private static Power build(ClassKit.PowerSpec spec) {
        PowerTier tier = PowerTier.valueOf(spec.tier());
        return new Power(ClassIds.id(spec.path()), tier, ClassIds.id(spec.classId()), RCPerks.skill(spec.skill()),
                PowerEligibility.governingSkillRequirement(tier),
                new ResourceLocation(ClassIds.MOD_ID, "textures/power/" + spec.path() + ".png"),
                spec.icdTicks(), null);
    }

    @Nullable
    public static String ownerOf(String powerPath) {
        return OWNER.get(powerPath);
    }

    @Nullable
    public static Power get(String path) {
        RegistryObject<Power> object = BY_PATH.get(path);
        return object != null && object.isPresent() ? object.get() : null;
    }

    @Nullable
    public static RegistryObject<Power> object(String path) {
        return BY_PATH.get(path);
    }

    @Nullable
    public static ClassKit.PowerSpec spec(String path) {
        return SPECS.get(path);
    }

    public static Map<String, RegistryObject<Power>> all() {
        return Collections.unmodifiableMap(BY_PATH);
    }

    /**
     * Re-derives the class Powers' governing gates from Runic Skills' tier configuration when it has
     * changed since the last call. Cheap enough to call from a periodic tick on either side.
     */
    public static boolean refreshGatesIfChanged() {
        int[] gates = new int[]{
                PowerEligibility.governingSkillRequirement(PowerTier.MARK),
                PowerEligibility.governingSkillRequirement(PowerTier.SEAL),
                PowerEligibility.governingSkillRequirement(PowerTier.CROWN)};
        if (java.util.Arrays.equals(gates, adoptedGates)) return false;
        for (Map.Entry<String, RegistryObject<Power>> entry : BY_PATH.entrySet()) {
            if (!entry.getValue().isPresent()) continue;
            entry.getValue().get().adoptTunables(build(SPECS.get(entry.getKey())));
        }
        adoptedGates = gates;
        return true;
    }
}
