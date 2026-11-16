package com.otectus.runicclasses.power.handlers;

import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Powers;
import com.otectus.runicclasses.kit.SpellCast;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Wizard Powers. Mark: Studied Strike (remembers up to 64 kinds of creature in the class state, so the
 * study survives relog); Seal: Recitation; Crown: Grand Working.
 */
public final class WizardPowers implements ClassModule {

    static final String STUDIED_STRIKE = "cls_wizard_studied_strike";
    static final String RECITATION = "cls_wizard_recitation";
    static final String GRAND_WORKING = "cls_wizard_grand_working";

    @Override
    public void outgoing(Hit hit) {
        if (!hit.magic) return;
        ServerPlayer p = hit.member;
        ClassState state = RCCapabilities.get(p);
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(hit.target.getType());
        if (state != null && type != null && state.studiedTypes.contains(type.toString())
                && Powers.trigger(p, STUDIED_STRIKE, hit.now, hit.target)) {
            hit.addPercent(Powers.value(STUDIED_STRIKE, "damage_bonus", 0.15));
        }
        if (!IronsSpellsBridge.active()) {
            CombatMemory memory = CombatMemory.of(p);
            if (memory.consume("grand_working_ready", hit.now)) {
                hit.addPercent(1.0);
            } else {
                arm(p, memory, hit.now);
            }
        }
    }

    @Override
    public void spellCast(ServerPlayer member, SpellCast cast) {
        CombatMemory memory = CombatMemory.of(member);
        long now = member.level().getGameTime();
        if (memory.consume("grand_working_ready", now)) {
            cast.addLevels(Powers.ticks(GRAND_WORKING, "levels", 2));
            cast.makeFree();
            return;
        }
        arm(member, memory, now);
    }

    /** Five casts (or, without spells, five magic hits) within the window arm the next one. */
    private static void arm(ServerPlayer p, CombatMemory memory, long now) {
        if (!Powers.ready(p, GRAND_WORKING)) return;
        int count = memory.recordWithin("grand_working", now, Powers.ticks(GRAND_WORKING, "window_ticks", 300));
        if (count < Math.max(1, Powers.ticks(GRAND_WORKING, "casts", 5)) || !Powers.startCooldown(p, GRAND_WORKING, now)) return;
        Powers.proc(p, GRAND_WORKING, p);
        memory.clearWithin("grand_working");
        memory.open("grand_working_ready", now + 20 * 30);
    }

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        ClassState state = RCCapabilities.get(member);
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (state != null && type != null) state.rememberStudied(type.toString());

        if (!Combat.isMagic(source)) return;
        long now = member.level().getGameTime();
        String spell = IronsSpellsBridge.spellIdOf(source);
        if (IronsSpellsBridge.active()) {
            if (spell != null && Powers.ready(member, RECITATION) && Powers.startCooldown(member, RECITATION, now)
                    && IronsSpellsBridge.refundCooldown(member, spell, Powers.value(RECITATION, "refund_fraction", 0.5))) {
                Powers.proc(member, RECITATION, victim);
            }
        } else if (Powers.trigger(member, RECITATION, now, member)) {
            member.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Powers.ticks(RECITATION, "duration_ticks", 100), 1));
        }
    }
}
