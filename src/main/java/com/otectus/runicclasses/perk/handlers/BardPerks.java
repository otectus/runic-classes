package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.EffectDurations;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicclasses.registry.RCEffects;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.living.MobEffectEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Bard (Intelligence / Fortune) — Cadence. */
public final class BardPerks implements ClassModule {

    static final String QUICK_STUDY = "cls_bard_quick_study";
    static final String SILVER_TONGUE = "cls_bard_silver_tongue";
    static final String BATTLE_HYMN = "cls_bard_battle_hymn";
    static final String HEXBREAKING_HYMN = "cls_bard_hexbreaking_hymn";
    static final String CRESCENDO = "cls_bard_crescendo";
    // Fortune's Chord is an attribute (PerkAttributes).

    /** One Hexbreaking Hymn per effect, however many Bards stand near it. */
    private static final Set<MobEffectInstance> CHARMED = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public double levelUpDiscount(Player member, Skill skill) {
        return Perks.on(member, QUICK_STUDY) ? Perks.pct(QUICK_STUDY, 0) : 0.0;
    }

    /**
     * Vanilla villagers only (OQ-12): a vanilla {@code Villager} clears every special price when trading
     * stops, so a discount added as the menu opens lasts exactly as long as this trade. MCA villagers and
     * wandering traders keep their own pricing and are left alone.
     */
    @Override
    public void containerOpened(ServerPlayer member, AbstractContainerMenu menu) {
        if (!(menu instanceof MerchantMenu) || !Perks.on(member, SILVER_TONGUE)) return;
        double share = Perks.pct(SILVER_TONGUE, 0);
        for (Villager villager : member.level().getEntitiesOfClass(Villager.class, member.getBoundingBox().inflate(8.0),
                v -> v.getTradingPlayer() == member && v.getType() == EntityType.VILLAGER)) {
            for (MerchantOffer offer : villager.getOffers()) {
                int base = offer.getBaseCostA().getCount();
                int discount = Math.max(base >= 2 ? 1 : 0, (int) Math.round(base * share));
                if (discount > 0) offer.addToSpecialPriceDiff(-discount);
            }
        }
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        if (!Perks.on(member, BATTLE_HYMN) || !Combat.inCombat(member, now)) return;
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Perks.v(BATTLE_HYMN, 0), true)) {
            ally.addEffect(new MobEffectInstance(RCEffects.BATTLE_HYMN.get(), 40, 0, true, false, true));
        }
    }

    @Override
    public void allyEffectAdded(ServerPlayer member, LivingEntity ally, MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL || !Perks.on(member, HEXBREAKING_HYMN)) return;
        if (ally.distanceTo(member) > Perks.v(HEXBREAKING_HYMN, 0) || !CHARMED.add(instance)) return;
        EffectDurations.scale(instance, Math.max(0.1, 1.0 - Perks.pct(HEXBREAKING_HYMN, 1)));
    }

    @Override
    public void allyHit(ServerPlayer member, LivingEntity ally, Hit hit) {
        if (!Perks.on(member, CRESCENDO) || ally.distanceTo(member) > Perks.v(CRESCENDO, 0)) return;
        CombatMemory memory = CombatMemory.of(member);
        if (memory.increment("crescendo") < 10) return;
        memory.reset("crescendo");
        float heal = (float) Perks.v(CRESCENDO, 1);
        for (LivingEntity friend : Combat.alliesNear(member, member.position(), Perks.v(CRESCENDO, 0), true)) friend.heal(heal);
    }
}
