package com.otectus.runicclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** {@code runicclasses-common.toml}. */
public final class RCCommonConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        DEBUG_LOGGING = b.comment("Log every class grant, removal and starter-bag decision at INFO instead of DEBUG.")
                .define("debugLogging", false);
        SPEC = b.build();
    }

    private RCCommonConfig() {}

    public static boolean debug() {
        return RCServerConfig.get(DEBUG_LOGGING, false);
    }
}
