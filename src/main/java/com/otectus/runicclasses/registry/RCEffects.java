package com.otectus.runicclasses.registry;

import com.otectus.runicclasses.data.ClassIds;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;

/**
 * Short-lived auras a class member spreads to allies. An effect rather than a hand-managed attribute
 * modifier, so it expires on its own when the ally walks away or the member leaves combat, survives
 * nothing it should not, and shows the ally an icon saying where the bonus came from. The magnitude
 * is read from the owning Perk each time the effect is applied, so it follows the class data.
 */
public final class RCEffects {

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ClassIds.MOD_ID);

    public static final RegistryObject<MobEffect> BATTLE_HYMN = EFFECTS.register("battle_hymn",
            () -> new ClassAuraEffect(0xB25FA8, () -> RCPerks.value("cls_bard_battle_hymn", 1) / 100.0)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, "6f1d6a3e-5d8a-4c1b-9b41-3a7e0c1b2d01", 0.08,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    public static final RegistryObject<MobEffect> VALIANT_PRESENCE = EFFECTS.register("valiant_presence",
            () -> new ClassAuraEffect(0xF0D27A, () -> RCPerks.value("cls_paladin_valiant_presence", 1))
                    .addAttributeModifier(Attributes.ARMOR, "6f1d6a3e-5d8a-4c1b-9b41-3a7e0c1b2d02", 1.0,
                            AttributeModifier.Operation.ADDITION));

    /** Cleric's Beacon: the damage bonus is applied by the hit dispatcher to anyone carrying it. */
    public static final RegistryObject<MobEffect> BEACON = EFFECTS.register("beacon",
            () -> new ClassAuraEffect(0xE0C25A, null));

    private RCEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    public static class ClassAuraEffect extends MobEffect {

        @Nullable private final DoubleSupplier amount;

        public ClassAuraEffect(int color, @Nullable DoubleSupplier amount) {
            super(MobEffectCategory.BENEFICIAL, color);
            this.amount = amount;
        }

        @Override
        public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
            if (amount == null) return super.getAttributeModifierValue(amplifier, modifier);
            double value = amount.getAsDouble();
            return Double.isFinite(value) && value > 0 ? value * (amplifier + 1) : 0.0;
        }
    }
}
