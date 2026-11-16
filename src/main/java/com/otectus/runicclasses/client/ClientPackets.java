package com.otectus.runicclasses.client;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.service.ClassHelper;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Client halves of the two packets. Only ever reached through {@code DistExecutor} on the client. */
public final class ClientPackets {

    /** A state that arrived before the local player existed (login race); applied on the next tick. */
    private static CompoundTag pendingState;

    private ClientPackets() {}

    public static void applyState(CompoundTag tag) {
        LocalPlayer player = Minecraft.getInstance().player;
        ClassState state = RCCapabilities.get(player);
        if (state == null) {
            pendingState = tag.copy();
            return;
        }
        state.deserializeNBT(tag);
        ClassHelper.invalidate(player.getUUID());
        pendingState = null;
    }

    /** Called from the client tick: deliver a state that raced ahead of the player entity. */
    static void flushPending() {
        if (pendingState == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || RCCapabilities.get(player) == null) return;
        applyState(pendingState);
    }

    static void forgetPending() {
        pendingState = null;
    }

    public static void applyDefinitions(Map<ResourceLocation, String> raw, int revision) {
        ClassDataManager.commitClient(raw, revision);
    }
}
