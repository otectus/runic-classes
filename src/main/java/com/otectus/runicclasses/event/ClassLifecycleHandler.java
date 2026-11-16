package com.otectus.runicclasses.event;

import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassDefinitionLoader;
import com.otectus.runicclasses.data.ClassDefinitionValidator;
import com.otectus.runicclasses.kit.BarbarianKnockback;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.KitDispatcher;
import com.otectus.runicclasses.kit.PerkAttributes;
import com.otectus.runicclasses.network.RCNetwork;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.service.ClassHelper;
import com.otectus.runicclasses.service.ClassReconciler;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * When the class layer is reconciled against the applied record (docs/plan.md 2.5): login (after
 * Runic Skills' own login handler, hence {@code LOW}), respawn, clone, every ten ticks, after a
 * datapack reload, and whenever RunicClasses changes the layer itself. Death, dimension change and
 * relog change nothing: the capability clones, the Origins container persists, the memo recomputes.
 */
public final class ClassLifecycleHandler {

    private ClassLifecycleHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ClassHelper.invalidate(player.getUUID());
        RCNetwork.syncDefinitions(player);
        ClassReconciler.reconcile(player);
        PerkAttributes.refresh(player);
        RCNetwork.syncState(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClassHelper.invalidate(event.getEntity().getUUID());
        ClassReconciler.forget(event.getEntity().getUUID());
        CombatMemory.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer fresh)) return;
        event.getOriginal().reviveCaps();
        ClassState old = RCCapabilities.get(event.getOriginal());
        ClassState now = RCCapabilities.get(fresh);
        if (old != null && now != null) now.copyFrom(old);
        event.getOriginal().invalidateCaps();
        ClassHelper.invalidate(fresh.getUUID());
        if (event.isWasDeath()) CombatMemory.onDeath(fresh.getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ClassReconciler.reconcile(player);
        PerkAttributes.refresh(player);
        RCNetwork.syncState(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RCNetwork.syncState(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer || player.tickCount % 10 != 5) return;
        ClassReconciler.reconcile(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ClassDataManager.takePendingReconcile()) return;
        // Definitions changed (a /reload committed new data): every online player is checked against
        // them now instead of waiting for their next periodic pass.
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) ClassReconciler.reconcile(player);
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ClassDefinitionLoader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Login sends its own snapshot from onLogin; this is the /reload path.
        if (event.getPlayer() != null) return;
        for (ServerPlayer player : event.getPlayerList().getPlayers()) RCNetwork.syncDefinitions(player);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RCPowers.refreshGatesIfChanged();
        for (ClassDefinitionValidator.Problem problem : ClassDefinitionValidator.validateOrigins(ClassDataManager.server().byId())) {
            RunicClasses.LOGGER.warn("Class data: {}", problem.message());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ClassHelper.clearAll();
        CombatMemory.clearAll();
        Deferred.clear();
        BarbarianKnockback.clear();
        KitDispatcher.clear();
    }
}
