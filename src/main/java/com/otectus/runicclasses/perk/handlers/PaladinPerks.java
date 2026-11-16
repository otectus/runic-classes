package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Combat;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicclasses.registry.RCEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Paladin (Constitution / Wisdom) — the Oath. */
public final class PaladinPerks implements ClassModule {

    static final String LAYING_OF_HANDS = "cls_paladin_laying_of_hands";
    static final String VALIANT_PRESENCE = "cls_paladin_valiant_presence";
    static final String SMITE = "cls_paladin_smite";
    static final String BULWARK = "cls_paladin_bulwark";
    static final String OATHKEEPER = "cls_paladin_oathkeeper";
    // Devout Bulwark is an attribute (PerkAttributes).

    @Override
    public void interact(ServerPlayer member, PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !member.isShiftKeyDown() || !member.getMainHandItem().isEmpty()) return;
        if (!(event.getTarget() instanceof LivingEntity target) || target == member || !Combat.isAlly(member, target)) return;
        if (!Perks.on(member, LAYING_OF_HANDS) || !Perks.ready(member, LAYING_OF_HANDS)) return;
        if (target.getHealth() >= target.getMaxHealth()) return;
        target.heal((float) Perks.v(LAYING_OF_HANDS, 0));
        Perks.startCooldown(member, LAYING_OF_HANDS, Perks.ticks(LAYING_OF_HANDS, 1));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        if (!Perks.on(member, VALIANT_PRESENCE)) return;
        for (LivingEntity ally : Combat.alliesNear(member, member.position(), Perks.v(VALIANT_PRESENCE, 0), true)) {
            ally.addEffect(new MobEffectInstance(RCEffects.VALIANT_PRESENCE.get(), 40, 0, true, false, true));
        }
    }

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee || !Perks.on(hit.member, SMITE)) return;
        MobType type = hit.target.getMobType();
        if (type == MobType.UNDEAD || type == MobType.ILLAGER) hit.addPercent(Perks.pct(SMITE, 0));
    }

    @Override
    public void blocked(ServerPlayer member, ShieldBlockEvent event) {
        if (Perks.on(member, BULWARK) && Perks.ready(member, BULWARK)) {
            member.heal((float) Perks.v(BULWARK, 0));
            Perks.startCooldown(member, BULWARK, Perks.ticks(BULWARK, 1));
        }
    }

    @Override
    public boolean allySurvive(ServerPlayer member, Player dying, LivingDeathEvent event) {
        if (!Perks.on(member, OATHKEEPER) || Perks.unsurvivable(event.getSource())) return false;
        if (member.distanceTo(dying) > Perks.v(OATHKEEPER, 1)) return false;
        if (Perks.surviveLocked(dying) || !Perks.ready(member, OATHKEEPER)) return false;
        dying.setHealth((float) Math.max(1.0, dying.getMaxHealth() * Perks.pct(OATHKEEPER, 2)));
        Perks.startCooldown(member, OATHKEEPER, Perks.ticks(OATHKEEPER, 0));
        Perks.lockSurvive(dying);
        return true;
    }
}
