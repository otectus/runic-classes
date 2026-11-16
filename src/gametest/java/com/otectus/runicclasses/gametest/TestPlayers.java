package com.otectus.runicclasses.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.service.ClassReconciler;
import io.github.edwinmindcraft.origins.api.OriginsAPI;
import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Test players with a live in-memory connection (the Runic Skills harness's approach): packets the
 * mod and Origins send are written to an {@link EmbeddedChannel} instead of a socket.
 */
final class TestPlayers {

    private TestPlayers() {}

    static ServerPlayer connected(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("runicclasses-gametest:" + name).getBytes()), name);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player);
        return player;
    }

    static IOriginContainer container(ServerPlayer player) {
        IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
        if (container == null) throw new GameTestAssertException("player has no Origins container");
        return container;
    }

    /** Origins' own human: no powers, so it cannot change health or damage under a test. */
    private static final ResourceLocation NEUTRAL = new ResourceLocation("origins", "human");

    /**
     * Chooses an origin on every active layer except the class layer, as a finished heritage/race flow
     * would. Deterministic: the power-free human where a layer offers it, else the lowest id — the
     * layer's origin set has no order, and a random origin changed max health between runs.
     */
    static void completeOtherLayers(ServerPlayer player) {
        IOriginContainer container = container(player);
        for (Holder.Reference<OriginLayer> layer : OriginsAPI.getActiveLayers()) {
            if (layer.key().equals(ClassIds.CLASS_LAYER) || container.hasOrigin(layer)) continue;
            ResourceKey<Origin> pick = null;
            for (Holder<Origin> origin : layer.value().origins(player)) {
                if (origin.unwrapKey().isEmpty() || !origin.value().isChoosable()) continue;
                ResourceKey<Origin> key = origin.unwrapKey().get();
                if (key.location().equals(NEUTRAL)) {
                    pick = key;
                    break;
                }
                if (pick == null || key.location().toString().compareTo(pick.location().toString()) < 0) pick = key;
            }
            if (pick != null) container.setOrigin(layer.key(), pick);
        }
    }

    /** Puts the player in {@code classPath} ({@code classless} included) and reconciles. */
    static void choose(ServerPlayer player, String classPath) {
        IOriginContainer container = container(player);
        ResourceKey<Origin> key = ClassIds.originKey(new ResourceLocation(ClassIds.MOD_ID, classPath));
        container.setOrigin(ClassIds.CLASS_LAYER, key);
        ClassReconciler.reconcile(player);
    }

    static void clearClass(ServerPlayer player) {
        container(player).setOrigin(ClassIds.CLASS_LAYER, ClassIds.EMPTY_ORIGIN);
        ClassReconciler.reconcile(player);
    }
}
