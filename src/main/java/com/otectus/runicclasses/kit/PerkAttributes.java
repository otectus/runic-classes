package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicskills.registry.RegistryAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Class Perks whose whole effect is an attribute: transient modifiers with a fixed UUID per Perk,
 * reconciled against "is the Perk in effect, and does its condition hold" every ten ticks and after
 * every grant change. Transient, so nothing is written into the player's save that would outlive the
 * mod, and a class swap leaves no stale modifier behind (docs/plan.md 4).
 */
public final class PerkAttributes {

    /** Blocks that count as natural ground for the Druid (dirt family, leaves, logs, planks). */
    public static final TagKey<Block> NATURAL_GROUND = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            new ResourceLocation("runicclasses", "natural_ground"));

    private record Mod(String perk, Supplier<Attribute> attribute, AttributeModifier.Operation operation,
                       java.util.function.ToDoubleFunction<Double> scale, Predicate<ServerPlayer> condition, UUID uuid) {}

    private static final Predicate<ServerPlayer> ALWAYS = p -> true;

    private static final List<Mod> MODS = List.of(
            mod("cls_barbarian_thick_hide", () -> Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_fighter_armor_mastery", () -> Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION, v -> v,
                    p -> p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem),
            mod("cls_monk_flowing_step", () -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, v -> v / 100.0,
                    Combat::lightChest),
            mod("cls_monk_flurry", () -> Attributes.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, v -> v / 100.0,
                    Combat::unarmed),
            mod("cls_rogue_light_fingers", () -> Attributes.LUCK, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_ranger_trailwise", () -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, v -> v / 100.0,
                    PerkAttributes::inWildBiome),
            mod("cls_bard_fortunes_chord", () -> Attributes.LUCK, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_druid_bark_skin", () -> Attributes.ARMOR, AttributeModifier.Operation.ADDITION, v -> v,
                    PerkAttributes::onNaturalGround),
            mod("cls_sorcerer_innate_reserves", () -> named("irons_spellbooks:max_mana"), AttributeModifier.Operation.MULTIPLY_BASE,
                    v -> v / 100.0, p -> IronsSpellsBridge.isLoaded()),
            mod("cls_sorcerer_hardy_vessel", () -> Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_sorcerer_arcane_skin", RegistryAttributes.MAGIC_RESIST, AttributeModifier.Operation.ADDITION, v -> v / 100.0, ALWAYS),
            mod("cls_wizard_scholars_eye", RegistryAttributes.ENCHANTING_POWER, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_wizard_arcane_focus", () -> named("irons_spellbooks:cooldown_reduction"), AttributeModifier.Operation.MULTIPLY_BASE,
                    v -> v / 100.0, p -> IronsSpellsBridge.isLoaded()),
            withUuid(mod("cls_wizard_arcane_focus", RegistryAttributes.XP_BONUS, AttributeModifier.Operation.ADDITION,
                    v -> v / 100.0, p -> !IronsSpellsBridge.isLoaded()), "cls_wizard_arcane_focus#xp"),
            mod("cls_wizard_counter_ward", RegistryAttributes.MAGIC_RESIST, AttributeModifier.Operation.ADDITION, v -> v / 100.0, ALWAYS),
            mod("cls_artificer_field_notes", RegistryAttributes.XP_BONUS, AttributeModifier.Operation.ADDITION, v -> v / 100.0, ALWAYS),
            mod("cls_artificer_tinkers_eye", RegistryAttributes.REPAIR_EFFICIENCY, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_artificer_steady_hands", RegistryAttributes.CRAFTING_LUCK, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS),
            mod("cls_paladin_devout_bulwark", () -> Attributes.ARMOR, AttributeModifier.Operation.ADDITION, v -> v, ALWAYS));

    private PerkAttributes() {}

    private static Mod mod(String perk, Supplier<Attribute> attribute, AttributeModifier.Operation op,
                           java.util.function.ToDoubleFunction<Double> scale, Predicate<ServerPlayer> condition) {
        return new Mod(perk, attribute, op, scale, condition, uuid(perk));
    }

    private static Mod withUuid(Mod mod, String seed) {
        return new Mod(mod.perk(), mod.attribute(), mod.operation(), mod.scale(), mod.condition(), uuid(seed));
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(("runicclasses:" + seed).getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static Attribute named(String id) {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id));
    }

    /** Brings every attribute Perk's modifier in line with its current state. */
    public static void refresh(ServerPlayer player) {
        if (player.isRemoved()) return;
        for (Mod mod : MODS) {
            Attribute attribute;
            try {
                attribute = mod.attribute().get();
            } catch (RuntimeException e) {
                continue;
            }
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            double amount = 0.0;
            if (RCPerks.on(player, mod.perk()) && mod.condition().test(player)) {
                amount = mod.scale().applyAsDouble(RCPerks.value(mod.perk(), 0));
            }
            AttributeModifier existing = instance.getModifier(mod.uuid());
            if (amount == 0.0 || !Double.isFinite(amount)) {
                if (existing != null) instance.removeModifier(mod.uuid());
                continue;
            }
            if (existing != null && existing.getAmount() == amount && existing.getOperation() == mod.operation()) continue;
            if (existing != null) instance.removeModifier(mod.uuid());
            instance.addTransientModifier(new AttributeModifier(mod.uuid(), "runicclasses:" + mod.perk(), amount, mod.operation()));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Removes every class attribute modifier (used when a player leaves for good). */
    public static void clear(ServerPlayer player) {
        for (Mod mod : MODS) {
            Attribute attribute;
            try {
                attribute = mod.attribute().get();
            } catch (RuntimeException e) {
                continue;
            }
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && instance.getModifier(mod.uuid()) != null) instance.removeModifier(mod.uuid());
        }
    }

    static boolean inWildBiome(ServerPlayer player) {
        var biome = player.level().getBiome(player.blockPosition());
        return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE);
    }

    public static boolean onNaturalGround(ServerPlayer player) {
        BlockPos below = player.getOnPos();
        BlockState state = player.level().getBlockState(below);
        return state.is(NATURAL_GROUND);
    }
}
