package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.living.LootingLevelEvent;

/** Ranger (Dexterity / Wisdom) — The Long Watch. */
public final class RangerPerks implements ClassModule {

    static final String STEADY_DRAW = "cls_ranger_steady_draw";
    static final String QUARRY_BRAND = "cls_ranger_quarry_brand";
    static final String FIELD_DRESSING = "cls_ranger_field_dressing";
    static final String DEADEYE = "cls_ranger_deadeye";
    static final String WARDEN = "cls_ranger_warden_of_the_wild";
    // Trailwise is an attribute (PerkAttributes).

    @Override
    public void outgoing(Hit hit) {
        if (!hit.projectile) return;
        ServerPlayer p = hit.member;
        if (Perks.on(p, STEADY_DRAW)) hit.addPercent(Perks.pct(STEADY_DRAW, 0));
        if (Perks.on(p, DEADEYE) && hit.source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()) {
            hit.addPercent(Perks.pct(DEADEYE, 0));
        }
        if (Perks.on(p, QUARRY_BRAND)) {
            hit.target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Perks.ticks(QUARRY_BRAND, 0), 0), p);
        }
    }

    @Override
    public void petOutgoing(Hit hit) {
        if (Perks.on(hit.member, WARDEN)) hit.addPercent(Perks.pct(WARDEN, 0));
    }

    @Override
    public void incoming(Hit hit) {
        if (Perks.on(hit.member, WARDEN) && Combat.isAnimalOrArthropod(hit.attacker)) hit.reduce(Perks.pct(WARDEN, 1));
    }

    @Override
    public void looting(ServerPlayer member, LivingEntity victim, LootingLevelEvent event) {
        if (Perks.on(member, FIELD_DRESSING) && Combat.isAnimalOrArthropod(victim)) {
            event.setLootingLevel(event.getLootingLevel() + (int) Perks.v(FIELD_DRESSING, 0));
        }
    }
}
