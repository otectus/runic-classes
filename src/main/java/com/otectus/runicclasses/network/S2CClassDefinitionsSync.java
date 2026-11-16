package com.otectus.runicclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The server's committed class definitions, as the JSON it loaded, so a client on a dedicated server
 * shows the server's perk gate levels and numbers. Bounded on decode: fourteen small files are
 * expected, and a payload far beyond that is refused rather than parsed.
 */
public record S2CClassDefinitionsSync(Map<ResourceLocation, String> raw, int revision) {

    private static final int MAX_FILES = 256;
    private static final int MAX_CHARS = 65_536;

    public static void encode(S2CClassDefinitionsSync msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.revision);
        buf.writeVarInt(msg.raw.size());
        for (Map.Entry<ResourceLocation, String> entry : msg.raw.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue(), MAX_CHARS);
        }
    }

    public static S2CClassDefinitionsSync decode(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_FILES) throw new io.netty.handler.codec.DecoderException("too many class definitions: " + count);
        Map<ResourceLocation, String> raw = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) raw.put(buf.readResourceLocation(), buf.readUtf(MAX_CHARS));
        return new S2CClassDefinitionsSync(raw, revision);
    }

    public static void handle(S2CClassDefinitionsSync msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.otectus.runicclasses.client.ClientPackets.applyDefinitions(msg.raw, msg.revision)));
        ctx.get().setPacketHandled(true);
    }
}
