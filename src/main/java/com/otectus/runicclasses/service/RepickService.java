package com.otectus.runicclasses.service;

import com.otectus.runicclasses.config.RCServerConfig;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.common.OriginsCommon;
import io.github.edwinmindcraft.origins.common.network.S2COpenOriginScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Re-opening the class screen: the Class Orb and {@code /runicclasses choose}.
 *
 * <p>Mirrors Origins' own Orb of Origin step for step, but on the class layer alone: set the layer to
 * {@code origins:empty}, re-check auto-choosing layers, send the container's sync packet and Origins'
 * {@code S2COpenOriginScreen}, then synchronise. The client builds its pending list from every active
 * layer without an origin, which is just the class layer, and Origins' own confirm path does the
 * rest. Valid here because no screen is open while an item is being used: Origins' reopen flag only
 * fires then (the reason Runic Races' Back button does not send this packet).
 *
 * <p>The reconciler runs immediately, so the player is Classless until they confirm — which is also
 * exactly what an operator's all-layer Orb of Origin leaves behind.
 */
public final class RepickService {

    /** Why a re-pick was refused, or {@code null} when it went ahead. */
    public record Result(@Nullable Component refusal) {

        public boolean ok() {
            return refusal == null;
        }
    }

    private RepickService() {}

    /**
     * @param fromOrb {@code true} for a player using an orb: the server switch, the never-mid-selection
     *                rule and the cooldown all apply. {@code false} for an operator command.
     */
    public static Result repick(ServerPlayer player, boolean fromOrb) {
        IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
        ClassState state = RCCapabilities.get(player);
        if (container == null || state == null) {
            return new Result(Component.translatable("message.runicclasses.orb.unavailable"));
        }
        long now = player.level().getGameTime();
        if (fromOrb) {
            if (!RCServerConfig.get(RCServerConfig.ALLOW_REPICK, true)) {
                return new Result(Component.translatable("message.runicclasses.orb.disabled"));
            }
            // Never mid-selection: heritage, race and class must all be chosen already.
            if (!container.hasAllOrigins() || !container.hasOrigin(ClassIds.CLASS_LAYER)) {
                return new Result(Component.translatable("message.runicclasses.orb.incomplete"));
            }
            int cooldown = RCServerConfig.get(RCServerConfig.REPICK_COOLDOWN_TICKS, 0);
            if (cooldown > 0 && state.lastRepickGameTime != Long.MIN_VALUE) {
                long remaining = state.lastRepickGameTime + cooldown - now;
                if (remaining > 0) {
                    return new Result(Component.translatable("message.runicclasses.orb.cooldown", (remaining + 19) / 20));
                }
            }
        }
        container.setOrigin(ClassIds.CLASS_LAYER, ClassIds.EMPTY_ORIGIN);
        container.checkAutoChoosingLayers(false);
        PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> player);
        OriginsCommon.CHANNEL.send(target, container.getSynchronizationPacket());
        OriginsCommon.CHANNEL.send(target, new S2COpenOriginScreen(false));
        container.synchronize();
        if (fromOrb) state.lastRepickGameTime = now;
        ClassReconciler.reconcile(player);
        return new Result(null);
    }

    /** Clears the class layer without prompting the player now (they are asked at their next login). */
    public static boolean clear(ServerPlayer player, boolean prompt) {
        IOriginContainer container = IOriginContainer.get(player).resolve().orElse(null);
        if (container == null) return false;
        container.setOrigin(ClassIds.CLASS_LAYER, ClassIds.EMPTY_ORIGIN);
        PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> player);
        OriginsCommon.CHANNEL.send(target, container.getSynchronizationPacket());
        if (prompt) OriginsCommon.CHANNEL.send(target, new S2COpenOriginScreen(false));
        container.synchronize();
        ClassReconciler.reconcile(player);
        return true;
    }
}
