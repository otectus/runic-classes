package com.otectus.runicclasses.client;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.service.ClassHelper;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client wiring. The Runic Skills hooks themselves are installed at construction on both sides (they
 * read the synced class state); this adds the per-client housekeeping and the Skills-screen badge.
 */
public final class RCClient {

    private static int ticks;

    private RCClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(RCClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(RCClient::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(RCClient::onScreenInit);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientPackets.flushPending();
        if (++ticks % 100 == 0) RCPowers.refreshGatesIfChanged();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClassDataManager.clearClient();
        ClientPackets.forgetPending();
        ClassHelper.clearAll();
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        SkillsScreenClassBadge.attach(event);
    }
}
