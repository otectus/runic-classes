package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.PerkAttributes;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Druid (Magic / Endurance) — the Green Covenant. */
public final class DruidPerks implements ClassModule {

    static final String HERB_CRAFT = "cls_druid_herb_craft";
    static final String THORNS = "cls_druid_thorns";
    static final String ROOTED = "cls_druid_rooted";
    static final String RAIN_BLESSED = "cls_druid_rain_blessed";
    static final String LORD_OF_THE_GROVE = "cls_druid_lord_of_the_grove";
    // Bark Skin is an attribute (PerkAttributes).

    public static final TagKey<Item> HERB_FOODS = TagKey.create(Registries.ITEM, new ResourceLocation("runicclasses", "herb_foods"));
    public static final TagKey<Block> GROVE_CROPS = TagKey.create(Registries.BLOCK, new ResourceLocation("runicclasses", "grove_crops"));

    /** Rooted asks for this long standing still before the first heal. */
    private static final long ROOTED_SETTLE_TICKS = 80;

    @Override
    public void usedItem(ServerPlayer member, ItemStack used) {
        if (used.isEdible() && used.is(HERB_FOODS) && Perks.on(member, HERB_CRAFT)) {
            member.getFoodData().eat((int) Perks.v(HERB_CRAFT, 0), 0.0f);
        }
    }

    @Override
    public void incoming(Hit hit) {
        LivingEntity attacker = hit.attacker;
        if (attacker == null || !hit.melee || attacker == hit.member) return;
        ServerPlayer p = hit.member;
        if (Perks.on(p, THORNS) && p.getRandom().nextDouble() < Perks.pct(THORNS, 0)) {
            float damage = (float) Perks.v(THORNS, 1);
            Deferred.later(() -> Combat.secondaryHurt(p, attacker, p.damageSources().thorns(p), damage));
        }
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        CombatMemory memory = CombatMemory.of(member);
        if (Perks.on(member, ROOTED) && now - memory.lastMoveTick >= ROOTED_SETTLE_TICKS && PerkAttributes.onNaturalGround(member)) {
            if (!memory.isOpen("rooted", now)) {
                memory.open("rooted", now + Perks.ticks(ROOTED, 1));
                member.heal((float) Perks.v(ROOTED, 0));
            }
        }
        if (Perks.on(member, RAIN_BLESSED) && member.isInWaterRainOrBubble()) {
            member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
        }
        if (Perks.on(member, LORD_OF_THE_GROVE) && !memory.isOpen("grove", now) && member.level() instanceof ServerLevel level) {
            memory.open("grove", now + Perks.ticks(LORD_OF_THE_GROVE, 0));
            int radius = (int) Math.round(Perks.v(LORD_OF_THE_GROVE, 1));
            BlockPos centre = member.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(GROVE_CROPS) && state.isRandomlyTicking()) state.randomTick(level, pos.immutable(), level.getRandom());
            }
        }
    }
}
