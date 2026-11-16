package com.otectus.runicclasses.data;

import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.api.registry.OriginsDynamicRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The ids other code needs to find the class layer. Kept free of game logic so a pack script can
 * load it (the Ultima selection bridge reads {@link #CLASS_LAYER}).
 */
public final class ClassIds {

    private ClassIds() {}

    public static final String MOD_ID = "runicclasses";

    /** The third Origins layer, order 20: after Runic Races' family (0) and race (10) layers. */
    public static final ResourceKey<OriginLayer> CLASS_LAYER = ResourceKey.create(
            OriginsDynamicRegistries.LAYERS_REGISTRY, new ResourceLocation(MOD_ID, "class"));

    /** The "no class" origin. A real, selectable origin, so the Origins flow never blocks on it. */
    public static final ResourceLocation CLASSLESS = new ResourceLocation(MOD_ID, "classless");

    /** Origins' sentinel for an unchosen layer; {@code hasOrigin} treats it like an unset one. */
    public static final ResourceKey<Origin> EMPTY_ORIGIN = ResourceKey.create(
            OriginsDynamicRegistries.ORIGINS_REGISTRY, new ResourceLocation("origins", "empty"));

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static ResourceKey<Origin> originKey(ResourceLocation id) {
        return ResourceKey.create(OriginsDynamicRegistries.ORIGINS_REGISTRY, id);
    }

    /** True for no origin at all and for Origins' empty sentinel. */
    public static boolean isEmpty(@Nullable ResourceKey<Origin> key) {
        return key == null || EMPTY_ORIGIN.location().equals(key.location());
    }
}
