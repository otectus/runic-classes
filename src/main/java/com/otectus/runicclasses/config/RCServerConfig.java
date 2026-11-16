package com.otectus.runicclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code runicclasses-server.toml}. Forge syncs a SERVER config to every connecting client, so the
 * client-side cap and budget hooks read the same switches the server enforces.
 *
 * <p>Behaviour switches only. Every per-class number (start level, cap bonus, perk gates and
 * magnitudes) lives in the class JSON under {@code data/runicclasses/classes}, one authority per
 * number and datapack-overridable; class Power cooldowns and magnitudes use Runic Skills' existing
 * Power override datapack ({@code data/runicclasses/powers/<power>.json}).
 */
public final class RCServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_REPICK;
    public static final ForgeConfigSpec.IntValue REPICK_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue CLASSLESS_GETS_UNIVERSAL_BAG;
    public static final ForgeConfigSpec.BooleanValue CLASS_FREE_LEVELS_EXTEND_GLOBAL_CAP;
    public static final ForgeConfigSpec.BooleanValue CLASS_FREE_LEVELS_COUNT_AS_EARNED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("repick");
        ALLOW_REPICK = b.comment("Whether a Class Orb may re-open the class screen. Operators can always use /runicclasses choose.")
                .define("allowRepick", true);
        REPICK_COOLDOWN_TICKS = b.comment("Game ticks a player must wait between two Class Orb uses. 0 = no wait.")
                .defineInRange("repickCooldownTicks", 0, 0, 20 * 60 * 60 * 24 * 7);
        b.pop();
        b.push("starterBag");
        CLASSLESS_GETS_UNIVERSAL_BAG = b.comment(
                        "true: a first confirmation of Classless grants the universal bag (runicclasses:bags/classless)",
                        "and uses up the one-time grant, so a class chosen later grants nothing (owner decision OQ-9).",
                        "false: Classless grants nothing and does not use up the grant; the first real class gets its bag.")
                .define("classlessGetsUniversalBag", true);
        b.pop();
        b.push("progression");
        CLASS_FREE_LEVELS_EXTEND_GLOBAL_CAP = b.comment(
                        "Whether the free starting levels a class grants raise the player's global level budget by the",
                        "same amount, so a class member keeps exactly as many purchasable levels as a Classless player.")
                .define("classFreeLevelsExtendGlobalCap", true);
        CLASS_FREE_LEVELS_COUNT_AS_EARNED = b.comment(
                        "Whether those free levels count as earned for the optional perk budget and for Power Points.",
                        "false keeps a class from accelerating either budget.")
                .define("classFreeLevelsCountAsEarned", false);
        b.pop();
        SPEC = b.build();
    }

    private RCServerConfig() {}

    /** Reads a value that may be asked for before the config has loaded (title screen, early ticks). */
    public static boolean get(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    public static int get(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
