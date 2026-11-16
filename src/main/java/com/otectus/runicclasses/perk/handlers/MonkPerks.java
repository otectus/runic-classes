package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/** Monk (Dexterity / Endurance) — Breath. */
public final class MonkPerks implements ClassModule {

    static final String OPEN_HAND = "cls_monk_open_hand";
    static final String CENTRED_BREATH = "cls_monk_centred_breath";
    static final String DEFLECT = "cls_monk_deflect";
    static final String STILLNESS = "cls_monk_stillness";
    // Flowing Step and Flurry are attributes (PerkAttributes).

    /** Stillness keeps its own Regeneration short and refreshed, so moving lets it lapse at once. */
    private static final int STILLNESS_REGEN_TICKS = 40;

    @Override
    public void outgoing(Hit hit) {
        if (hit.melee && Combat.unarmed(hit.member) && Perks.on(hit.member, OPEN_HAND)) {
            hit.addFlat(Perks.v(OPEN_HAND, 0));
        }
    }

    @Override
    public void fall(ServerPlayer member, LivingFallEvent event) {
        if (Perks.on(member, CENTRED_BREATH)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * (float) Math.max(0.0, 1.0 - Perks.pct(CENTRED_BREATH, 0)));
        }
    }

    @Override
    public boolean avoid(ServerPlayer member, LivingAttackEvent event) {
        return event.getSource().is(DamageTypeTags.IS_PROJECTILE) && Combat.unarmed(member)
                && Perks.on(member, DEFLECT) && member.getRandom().nextDouble() < Perks.pct(DEFLECT, 0);
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        if (!Perks.on(member, STILLNESS)) return;
        CombatMemory memory = CombatMemory.of(member);
        if (now - memory.lastMoveTick >= Perks.ticks(STILLNESS, 0)) {
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, STILLNESS_REGEN_TICKS, 0, true, false, true));
            memory.stillnessActive = true;
        } else if (memory.stillnessActive) {
            memory.stillnessActive = false;
            MobEffectInstance regen = member.getEffect(MobEffects.REGENERATION);
            if (regen != null && regen.getAmplifier() == 0 && regen.isAmbient() && regen.getDuration() <= STILLNESS_REGEN_TICKS) {
                member.removeEffect(MobEffects.REGENERATION);
            }
        }
    }
}
