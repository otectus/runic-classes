package com.otectus.runicclasses.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The owner's applied class state: what the client-side cap, budget and visibility hooks read. */
public record S2CClassStateSync(CompoundTag state) {

    public static void encode(S2CClassStateSync msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.state);
    }

    public static S2CClassStateSync decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new S2CClassStateSync(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CClassStateSync msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.otectus.runicclasses.client.ClientPackets.applyState(msg.state)));
        ctx.get().setPacketHandled(true);
    }
}
