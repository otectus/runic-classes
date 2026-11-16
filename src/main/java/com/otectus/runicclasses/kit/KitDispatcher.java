package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.registry.RCEffects;
import com.otectus.runicclasses.service.ClassHelper;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Routes Forge events to the class modules of the player concerned (docs/plan.md 2.8 "Behaviour").
 *
 * <p>Each event is subscribed once. The acting player's applied class is one memoised lookup
 * ({@link ClassHelper#appliedClass}), and only that class's Perks and Powers modules run, so a player's
 * outgoing bonuses all land in one {@link Hit} and are applied additively, once. Effects that reach
 * other players (auras, rescues, party heals) find nearby members through a member index rebuilt every
 * second rather than by scanning every module.
 */
public final class KitDispatcher {

    private static final Map<String, List<ClassModule>> MODULES = new HashMap<>();
    private static volatile Map<String, List<ServerPlayer>> online = Map.of();
    /** Each hurt event's amount before other listeners scaled it; weak, so a cancelled event cannot leak. */
    private static final Map<LivingHurtEvent, Float> UNSCALED = new WeakHashMap<>();

    private KitDispatcher() {}

    public static void register(String classPath, ClassModule module) {
        MODULES.computeIfAbsent(classPath, k -> new ArrayList<>()).add(module);
    }

    static List<ClassModule> modules(@Nullable Player player) {
        if (player == null || player instanceof FakePlayer) return List.of();
        String applied = ClassHelper.appliedClass(player);
        if (applied.isEmpty()) return List.of();
        List<ClassModule> list = MODULES.get(applied);
        return list == null ? List.of() : list;
    }

    @Nullable
    private static ServerPlayer member(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && !ClassHelper.appliedClass(player).isEmpty() ? player : null;
    }

    /** Online members of {@code classPath}, as of the last index rebuild (at most a second old). */
    public static List<ServerPlayer> onlineMembers(String classPath) {
        List<ServerPlayer> list = online.get(classPath);
        return list == null ? List.of() : list;
    }

    private static void rebuildIndex(MinecraftServer server) {
        Map<String, List<ServerPlayer>> next = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String applied = ClassHelper.appliedClass(player);
            if (!applied.isEmpty()) next.computeIfAbsent(applied, k -> new ArrayList<>()).add(player);
        }
        online = next;
    }

    public static int onlineMemberCount() {
        int n = 0;
        for (List<ServerPlayer> list : online.values()) n += list.size();
        return n;
    }

    // -- hits ------------------------------------------------------------------------------------

    /**
     * Class percentages stack additively with Runic Skills' on the same hit (docs/plan.md 4, OQ-15).
     * Runic Skills multiplies the amount in its own listeners, so {@link #onHurt} takes a class bonus as
     * a share of the amount recorded here, before any of them ran, and adds it on top.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurtFirst(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide()) UNSCALED.put(event, event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        long now = target.level().getGameTime();
        float amount = event.getAmount();
        Float recorded = UNSCALED.remove(event);
        float unscaled = recorded != null ? recorded : amount;
        Entity sourceEntity = source.getEntity();
        boolean primary = DamageContext.allowsStandardOutgoingModifiers();

        ServerPlayer attacker = member(sourceEntity);
        if (attacker != null && primary && attacker != target) {
            CombatMemory memory = CombatMemory.of(attacker);
            Hit hit = new Hit(attacker, attacker, target, source, amount, now);
            for (ClassModule module : modules(attacker)) module.outgoing(hit);
            amount = hit.applyOutgoing(amount, unscaled);
            memory.lastAttackTick = now;
            memory.lastCombatTick = now;
        } else if (sourceEntity instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner
                && member(owner) != null && primary) {
            Hit hit = new Hit(owner, pet, target, source, amount, now);
            for (ClassModule module : modules(owner)) module.petOutgoing(hit);
            amount = hit.applyOutgoing(amount, unscaled);
        }

        // Beacon (Cleric Crown): anyone carrying the light deals more, member or not.
        if (primary && sourceEntity instanceof LivingEntity living && living != target
                && living.hasEffect(RCEffects.BEACON.get())) {
            amount += (float) (unscaled * Powers.value("cls_cleric_beacon", "damage_bonus", 0.15));
        }

        // Crescendo (Bard): hits by allies near a Bard count toward the party heal.
        if (primary && sourceEntity instanceof LivingEntity ally && ally != target) {
            for (ServerPlayer bard : onlineMembers("bard")) {
                if (bard.level() == ally.level() && Combat.isAlly(bard, ally)) {
                    Hit hit = new Hit(bard, ally, target, source, amount, now);
                    for (ClassModule module : modules(bard)) module.allyHit(bard, ally, hit);
                }
            }
        }

        ServerPlayer victim = member(target);
        if (victim != null) {
            CombatMemory memory = CombatMemory.of(victim);
            LivingEntity attackerEntity = sourceEntity instanceof LivingEntity l ? l : null;
            Hit hit = new Hit(victim, attackerEntity, victim, source, amount, now);
            for (ClassModule module : modules(victim)) module.incoming(hit);
            amount = hit.applyIncoming(amount);
            memory.lastHurtTick = now;
            memory.lastCombatTick = now;
        }
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getAmount() <= 0) return;
        DamageSource source = event.getSource();
        long now = target.level().getGameTime();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;

        if (target instanceof Player || target instanceof TamableAnimal) {
            if (attacker != null && !(attacker instanceof Player)) CombatMemory.markAggressor(attacker, now);
        }

        FinalHit hit = new FinalHit(attacker, target, source, event.getAmount(), now);
        ServerPlayer victim = member(target);
        if (victim != null) {
            for (ClassModule module : modules(victim)) module.incomingFinal(hit);
        }
        // Party effects that react to an ally being hurt (Anthem, Ward of Light).
        if (target instanceof Player || target instanceof TamableAnimal) {
            for (String classPath : List.of("bard", "cleric")) {
                for (ServerPlayer member : onlineMembers(classPath)) {
                    if (member.level() != target.level() || !Combat.isAlly(member, target)) continue;
                    for (ClassModule module : modules(member)) module.allyDamaged(member, target, hit);
                }
            }
        }
        ServerPlayer striker = member(attacker);
        if (striker != null && striker != target && DamageContext.allowsStandardOutgoingModifiers()) {
            for (ClassModule module : modules(striker)) module.outgoingFinal(hit);
        }
        if (hit.amount() != event.getAmount()) event.setAmount(hit.amount());
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        ServerPlayer victim = member(event.getEntity());
        if (victim == null || event.getEntity().level().isClientSide()) return;
        for (ClassModule module : modules(victim)) {
            if (module.avoid(victim, event)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (dying.level().isClientSide()) return;
        if (dying instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            for (ClassModule module : modules(player)) {
                if (module.survive(player, event)) {
                    event.setCanceled(true);
                    return;
                }
            }
            for (ServerPlayer paladin : onlineMembers("paladin")) {
                if (paladin == player || paladin.level() != player.level() || !Combat.isAlly(paladin, player)) continue;
                for (ClassModule module : modules(paladin)) {
                    if (module.allySurvive(paladin, player, event)) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
            return;
        }
        ServerPlayer killer = member(event.getSource().getEntity());
        if (killer != null) {
            for (ClassModule module : modules(killer)) module.kill(killer, dying, event.getSource());
        }
    }

    // -- discrete combat events ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlock(ShieldBlockEvent event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null || member.level().isClientSide()) return;
        for (ClassModule module : modules(member)) module.blocked(member, event);
    }

    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null || event.getResult() == Event.Result.DENY) return;
        for (ClassModule module : modules(member)) module.crit(member, event);
    }

    @SubscribeEvent
    public static void onLooting(LootingLevelEvent event) {
        if (event.getDamageSource() == null) return;
        ServerPlayer member = member(event.getDamageSource().getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.looting(member, event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        BarbarianKnockback.apply(event);
    }

    // -- effects ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null || event.getEffectInstance() == null) return;
        for (ClassModule module : modules(member)) {
            if (!module.effectApplicable(member, event.getEffectInstance())) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getEffectInstance() == null) return;
        ServerPlayer self = member(target);
        if (self != null) for (ClassModule module : modules(self)) module.selfEffectAdded(self, event);

        Entity source = event.getEffectSource();
        ServerPlayer inflictor = source instanceof Projectile projectile ? member(projectile.getOwner()) : member(source);
        if (inflictor != null && inflictor != target) {
            for (ClassModule module : modules(inflictor)) module.effectInflicted(inflictor, target, event);
        }
        for (ServerPlayer bard : onlineMembers("bard")) {
            if (bard.level() != target.level() || !Combat.isAlly(bard, target)) continue;
            for (ClassModule module : modules(bard)) module.allyEffectAdded(bard, target, event);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null || member.level().isClientSide()) return;
        for (ClassModule module : modules(member)) module.heal(member, event);
    }

    // -- items, movement, interaction ------------------------------------------------------------

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.crafted(member, event.getCrafting());
    }

    @SubscribeEvent
    public static void onUsed(LivingEntityUseItemEvent.Finish event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.usedItem(member, event.getItem());
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.fall(member, event);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.breakSpeed(member, event);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.interact(member, event);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return;
        ServerPlayer member = member(projectile.getOwner());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.projectileSpawned(member, projectile);
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()) return;
        ServerPlayer member = member(projectile.getOwner());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.projectileImpact(member, event);
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        ServerPlayer member = member(event.getEntity());
        if (member == null) return;
        for (ClassModule module : modules(member)) module.containerOpened(member, event.getContainer());
    }

    // -- ticks -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        // Spread players over the ten-tick period instead of running everyone on the same tick.
        if ((player.tickCount + player.getId()) % 10 != 0) return;
        long now = player.level().getGameTime();
        PerkAttributes.refresh(player);
        if (member(player) == null) return;
        // Movement, sampled at this cadence, for the stand-still Perks (Stillness, Rooted).
        CombatMemory memory = CombatMemory.of(player);
        net.minecraft.world.phys.Vec3 pos = player.position();
        if (pos.distanceToSqr(memory.lastPos) > 0.0025) {
            memory.lastPos = pos;
            memory.lastMoveTick = now;
        }
        for (ClassModule module : modules(player)) module.tick(player, now);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Deferred.drain();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getTickCount() % 20 == 0) {
            rebuildIndex(server);
            RCPowersGateWatch.check();
        }
    }

    // -- entry points for the Iron's Spells bridge and the Runic Skills discount hook -------------

    public static void spellCast(ServerPlayer player, SpellCast cast) {
        for (ClassModule module : modules(player)) module.spellCast(player, cast);
    }

    public static int spellLevelBonus(ServerPlayer player) {
        int bonus = 0;
        for (ClassModule module : modules(player)) bonus += Math.max(0, module.spellLevelBonus(player));
        return bonus;
    }

    public static void spellHealed(ServerPlayer caster, LivingEntity target) {
        for (ClassModule module : modules(caster)) module.spellHealed(caster, target);
    }

    /** Both sides; the Skills screen prices a level with it. */
    public static double levelUpDiscount(Player player, Skill skill) {
        double discount = 0.0;
        for (ClassModule module : modules(player)) discount += Math.max(0.0, module.levelUpDiscount(player, skill));
        return discount;
    }

    public static void clear() {
        online = Map.of();
        RunicClasses.LOGGER.debug("Class effect state cleared.");
    }

    /** Re-derives class Power gates if Runic Skills' tier configuration changed (e.g. /skillsreload). */
    static final class RCPowersGateWatch {
        static void check() {
            try {
                com.otectus.runicclasses.registry.RCPowers.refreshGatesIfChanged();
            } catch (RuntimeException e) {
                RunicClasses.LOGGER.debug("Could not refresh class Power gates: {}", e.toString());
            }
        }
    }
}
