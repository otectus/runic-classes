package com.otectus.runicclasses.data;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * One class, as declared by {@code data/runicclasses/classes/<class>.json}.
 *
 * <p>Registries freeze at startup, so the Perks and Powers themselves are registered in code
 * ({@code RCPerks}, {@code RCPowers}); a definition <em>binds</em> those registered ids to a class and
 * carries every tunable number. Classless is a definition too, with no stats, Perks or Powers.
 *
 * @param primary   the Runic Skills Aptitude that governs the class Powers ({@code null} for Classless)
 * @param secondary the second class Aptitude ({@code null} for Classless)
 * @param color     {@code 0xRRGGBB}, for the Skills-screen badge
 */
public record ClassDefinition(ResourceLocation id,
                              ResourceLocation origin,
                              @Nullable ResourceLocation iconItem,
                              int color,
                              @Nullable ResourceLocation primary,
                              @Nullable ResourceLocation secondary,
                              int startLevel,
                              int capBonus,
                              List<PerkBinding> perks,
                              @Nullable PowerSet powers,
                              @Nullable StarterBag starterBag) {

    public enum Gate { PRIMARY, SECONDARY }

    /**
     * One class Perk: which Aptitude gates it, at what level, whether it starts enabled, and its
     * tooltip/handler numbers by key (keys absent here fall back to {@link ClassKit}'s defaults).
     */
    public record PerkBinding(ResourceLocation id, Gate gate, int level, boolean starting,
                              Map<String, Double> values) {

        public String path() {
            return id.getPath();
        }
    }

    public record PowerSet(ResourceLocation mark, ResourceLocation seal, ResourceLocation crown) {

        public List<ResourceLocation> all() {
            return List.of(mark, seal, crown);
        }
    }

    /**
     * @param lootTable the gift table rolled when there is no Treasure Bags bag to hand out
     * @param bagType   the Treasure Bags bag type wrapping that table, when the mod is present
     */
    public record StarterBag(ResourceLocation lootTable, @Nullable ResourceLocation bagType) {}

    public boolean isClassless() {
        return ClassIds.CLASSLESS.equals(this.id);
    }

    /** The class's short name, {@code fighter}; also its Power school path. */
    public String path() {
        return this.id.getPath();
    }

    @Nullable
    public ResourceLocation statFor(Gate gate) {
        return gate == Gate.PRIMARY ? this.primary : this.secondary;
    }
}
