package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.EffectDurations;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;

/** Warlock (Magic / Fortune) — the Bargain. */
public final class WarlockPerks implements ClassModule {

    static final String DARK_BARGAIN = "cls_warlock_dark_bargain";
    static final String NIGHT_SIGHT = "cls_warlock_night_sight";
    static final String HEX = "cls_warlock_hex";
    static final String SOUL_TITHE = "cls_warlock_soul_tithe";
    static final String LINGERING_CURSE = "cls_warlock_lingering_curse";
    static final String PACT_OF_THE_DEEP = "cls_warlock_pact_of_the_deep";

    @Override
    public void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {
        if (Perks.on(member, DARK_BARGAIN)) member.getFoodData().eat((int) Perks.v(DARK_BARGAIN, 0), 0.0f);
        if (Perks.on(member, SOUL_TITHE) && member.getRandom().nextDouble() < Perks.pct(SOUL_TITHE, 0)
                && member.level() instanceof ServerLevel level) {
            ExperienceOrb.award(level, victim.position(), (int) Perks.v(SOUL_TITHE, 1));
        }
    }

    @Override
    public boolean effectApplicable(ServerPlayer member, MobEffectInstance effect) {
        return !((effect.getEffect() == MobEffects.BLINDNESS || effect.getEffect() == MobEffects.DARKNESS)
                && Perks.on(member, NIGHT_SIGHT));
    }

    @Override
    public void outgoing(Hit hit) {
        if (!(hit.melee || hit.projectile || hit.magic)) return;
        ServerPlayer p = hit.member;
        if (Perks.on(p, HEX)) {
            CombatMemory memory = CombatMemory.of(p);
            if (hit.now - memory.lastOn("hex", hit.target) >= Perks.ticks(HEX, 1)) {
                memory.markOn("hex", hit.target, hit.now);
                hit.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Perks.ticks(HEX, 0), 0), p);
            }
        }
        if (Perks.on(p, PACT_OF_THE_DEEP) && p.getHealth() < p.getMaxHealth() * Perks.pct(PACT_OF_THE_DEEP, 0)) {
            hit.addPercent(Perks.pct(PACT_OF_THE_DEEP, 1));
        }
    }

    @Override
    public void effectInflicted(ServerPlayer member, LivingEntity target, MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL && Perks.on(member, LINGERING_CURSE)) {
            EffectDurations.scale(instance, 1.0 + Perks.pct(LINGERING_CURSE, 0));
        }
    }
}
