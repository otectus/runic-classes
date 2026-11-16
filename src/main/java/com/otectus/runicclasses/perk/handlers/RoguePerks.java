package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.ApothicAttributesIntegration;
import com.otectus.runicskills.registry.RegistryAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;

/** Rogue (Dexterity / Fortune) — Edgework. */
public final class RoguePerks implements ClassModule {

    static final String BACKSTAB = "cls_rogue_backstab";
    static final String EVASIVE = "cls_rogue_evasive";
    static final String TREASURE_SENSE = "cls_rogue_treasure_sense";
    static final String ASSASSINATE = "cls_rogue_assassinate";
    static final String LUCKY_STRIKE = "cls_rogue_lucky_strike";
    // Light Fingers is an attribute (PerkAttributes).

    @Override
    public void outgoing(Hit hit) {
        ServerPlayer p = hit.member;
        if (hit.melee && Perks.on(p, BACKSTAB) && facingAway(hit.target, p.position())) {
            hit.addPercent(Perks.pct(BACKSTAB, 0));
        }
        if ((hit.melee || hit.projectile) && Perks.on(p, ASSASSINATE) && (p.isCrouching() || p.isInvisible())
                && hit.target.getHealth() >= hit.target.getMaxHealth()) {
            hit.addPercent(Perks.pct(ASSASSINATE, 0));
        }
    }

    /** True when {@code from} is behind the target's line of sight. */
    static boolean facingAway(LivingEntity target, Vec3 from) {
        Vec3 toAttacker = from.subtract(target.position());
        if (toAttacker.lengthSqr() < 1.0E-6) return false;
        return target.getViewVector(1.0F).dot(toAttacker.normalize()) < 0.0;
    }

    @Override
    public boolean avoid(ServerPlayer member, LivingAttackEvent event) {
        var source = event.getSource();
        boolean melee = source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_PROJECTILE);
        return melee && Perks.on(member, EVASIVE) && member.getRandom().nextDouble() < Perks.pct(EVASIVE, 0);
    }

    @Override
    public void looting(ServerPlayer member, LivingEntity victim, LootingLevelEvent event) {
        if (Perks.on(member, TREASURE_SENSE)) event.setLootingLevel(event.getLootingLevel() + (int) Perks.v(TREASURE_SENSE, 0));
    }

    @Override
    public void crit(ServerPlayer member, CriticalHitEvent event) {
        if (event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW || !Perks.on(member, LUCKY_STRIKE)) return;
        if (member.getRandom().nextDouble() >= Perks.pct(LUCKY_STRIKE, 0)) return;
        // Runic Skills' crit handler runs first (HIGHEST) and adds Critical Damage only to the crits it
        // sees; a crit forced here gets the same addition, unless Apothic owns crit damage.
        float modifier = Math.max(1.5f, event.getDamageModifier());
        boolean apothic = ApothicAttributesIntegration.isModLoaded() && HandlerCommonConfig.HANDLER.instance().apothicDelegateCritDamage;
        if (!apothic) modifier += (float) member.getAttributeValue(RegistryAttributes.CRITICAL_DAMAGE.get());
        event.setResult(Event.Result.ALLOW);
        event.setDamageModifier(modifier);
    }
}
