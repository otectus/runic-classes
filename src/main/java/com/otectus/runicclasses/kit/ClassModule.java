package com.otectus.runicclasses.kit;

import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * One class's Perks or Powers. {@link KitDispatcher} subscribes to each Forge event once and calls only
 * the modules of the class the player actually has, so an event costs one memoised class lookup rather
 * than one membership check per module, and every outgoing bonus of a class lands in one additive sum
 * (docs/plan.md 4: class bonuses stack additively).
 *
 * <p>Every method is a no-op by default; a module overrides what its Perks or Powers react to. All are
 * server-side. "Member" is always the player whose class this module belongs to.
 */
public interface ClassModule {

    /** Outgoing hit by the member, before armour ({@code LivingHurtEvent}). Add bonuses to {@code hit}. */
    default void outgoing(Hit hit) {}

    /** Outgoing hit by one of the member's tamed animals, before armour. */
    default void petOutgoing(Hit hit) {}

    /** Incoming hit on the member, before armour. Add reductions to {@code hit}. */
    default void incoming(Hit hit) {}

    /** Outgoing hit by the member, final damage ({@code LivingDamageEvent}). */
    default void outgoingFinal(FinalHit hit) {}

    /** Incoming hit on the member, final damage. May lower {@link FinalHit#amount}. */
    default void incomingFinal(FinalHit hit) {}

    /** Before invulnerability ({@code LivingAttackEvent}): return true to make the member avoid the attack. */
    default boolean avoid(ServerPlayer member, LivingAttackEvent event) { return false; }

    /** The member killed {@code victim}. */
    default void kill(ServerPlayer member, LivingEntity victim, DamageSource source) {}

    /** The member is dying: return true to have survived (the event is cancelled by the dispatcher). */
    default boolean survive(ServerPlayer member, LivingDeathEvent event) { return false; }

    /** Another player near the member is dying: return true to have saved them. */
    default boolean allySurvive(ServerPlayer member, Player dying, LivingDeathEvent event) { return false; }

    /** An ally of the member (the member included) took final damage. */
    default void allyDamaged(ServerPlayer member, LivingEntity ally, FinalHit hit) {}

    /** An ally of the member (the member included) landed a hit. */
    default void allyHit(ServerPlayer member, LivingEntity ally, Hit hit) {}

    /** Every ten ticks while the member is online. */
    default void tick(ServerPlayer member, long now) {}

    default void blocked(ServerPlayer member, ShieldBlockEvent event) {}

    default void crit(ServerPlayer member, CriticalHitEvent event) {}

    default void looting(ServerPlayer member, LivingEntity victim, LootingLevelEvent event) {}

    /** Return false to stop {@code effect} reaching the member at all. */
    default boolean effectApplicable(ServerPlayer member, MobEffectInstance effect) { return true; }

    /** An effect is about to land on the member. */
    default void selfEffectAdded(ServerPlayer member, MobEffectEvent.Added event) {}

    /** An effect the member caused is about to land on someone else. */
    default void effectInflicted(ServerPlayer member, LivingEntity target, MobEffectEvent.Added event) {}

    /** An effect is about to land on an ally of the member (the member included). */
    default void allyEffectAdded(ServerPlayer member, LivingEntity ally, MobEffectEvent.Added event) {}

    default void heal(ServerPlayer member, LivingHealEvent event) {}

    default void crafted(ServerPlayer member, ItemStack crafted) {}

    default void usedItem(ServerPlayer member, ItemStack used) {}

    default void fall(ServerPlayer member, LivingFallEvent event) {}

    default void breakSpeed(ServerPlayer member, PlayerEvent.BreakSpeed event) {}

    default void interact(ServerPlayer member, PlayerInteractEvent.EntityInteract event) {}

    default void projectileSpawned(ServerPlayer member, Projectile projectile) {}

    default void projectileImpact(ServerPlayer member, ProjectileImpactEvent event) {}

    default void containerOpened(ServerPlayer member, AbstractContainerMenu menu) {}

    /** A spell is being cast (Iron's Spells). Adjust {@code cast} to change its level or mana cost. */
    default void spellCast(ServerPlayer member, SpellCast cast) {}

    /** Extra levels for every spell the member casts (Iron's Spells' {@code ModifySpellLevelEvent}). */
    default int spellLevelBonus(ServerPlayer member) { return 0; }

    /** The member healed {@code target} with a spell. */
    default void spellHealed(ServerPlayer member, LivingEntity target) {}

    /** A fractional discount on the member's next level of {@code skill}; read on both sides. */
    default double levelUpDiscount(Player member, Skill skill) { return 0.0; }
}
