package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.EffectDurations;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;

/** Cleric (Wisdom / Magic) — the Rites. */
public final class ClericPerks implements ClassModule {

    static final String BLESSED_VITALITY = "cls_cleric_blessed_vitality";
    static final String CONSECRATED_WEAPON = "cls_cleric_consecrated_weapon";
    static final String MENDING_TOUCH = "cls_cleric_mending_touch";
    static final String TURN_THE_DEAD = "cls_cleric_turn_the_dead";
    static final String SANCTUARY = "cls_cleric_sanctuary";
    static final String INTERCESSION = "cls_cleric_intercession";

    @Override
    public void selfEffectAdded(ServerPlayer member, MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().getCategory() == MobEffectCategory.BENEFICIAL && Perks.on(member, BLESSED_VITALITY)) {
            EffectDurations.scale(instance, 1.0 + Perks.pct(BLESSED_VITALITY, 0));
        }
    }

    @Override
    public void outgoing(Hit hit) {
        if (Combat.isUndead(hit.target) && Perks.on(hit.member, CONSECRATED_WEAPON)) hit.addPercent(Perks.pct(CONSECRATED_WEAPON, 0));
    }

    @Override
    public void incoming(Hit hit) {
        if (!(hit.attacker instanceof Mob mob) || !Combat.isUndead(mob) || !hit.melee) return;
        ServerPlayer p = hit.member;
        if (Perks.on(p, TURN_THE_DEAD) && p.getRandom().nextDouble() < Perks.pct(TURN_THE_DEAD, 0)) {
            mob.setTarget(null);
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Perks.ticks(TURN_THE_DEAD, 1), 1), p);
        }
    }

    @Override
    public void heal(ServerPlayer member, LivingHealEvent event) {
        // A drunk Instant Health heals inside finishUsingItem, while the potion is still the use item.
        if (Perks.on(member, MENDING_TOUCH) && member.isUsingItem()) {
            ItemStack using = member.getUseItem();
            if (using.getItem() instanceof PotionItem && PotionUtils.getMobEffects(using).stream()
                    .anyMatch(e -> e.getEffect() == MobEffects.HEAL)) {
                event.setAmount(event.getAmount() * (float) (1.0 + Perks.pct(MENDING_TOUCH, 0)));
            }
        }
        if (Perks.on(member, SANCTUARY) && member.getHealth() < member.getMaxHealth() * Perks.pct(SANCTUARY, 0)
                && Perks.ready(member, SANCTUARY)) {
            Perks.startCooldown(member, SANCTUARY, Perks.ticks(SANCTUARY, 2));
            member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Perks.ticks(SANCTUARY, 1), 0));
        }
    }

    @Override
    public boolean survive(ServerPlayer member, LivingDeathEvent event) {
        if (!Perks.on(member, INTERCESSION) || Perks.unsurvivable(event.getSource())) return false;
        if (Perks.surviveLocked(member) || !Perks.ready(member, INTERCESSION)) return false;
        member.setHealth((float) Math.max(1.0, member.getMaxHealth() * Perks.pct(INTERCESSION, 1)));
        Perks.startCooldown(member, INTERCESSION, Perks.ticks(INTERCESSION, 0));
        Perks.lockSurvive(member);
        return true;
    }
}
