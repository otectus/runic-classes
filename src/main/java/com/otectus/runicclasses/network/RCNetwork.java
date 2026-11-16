package com.otectus.runicclasses.network;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * {@code runicclasses:main}, protocol 1. Two server-to-client packets: the applied class state (the
 * class id itself the client reads from the Origins container, which Origins syncs) and the class
 * definitions. Screen opening reuses Origins' own packet, and re-picking is a server-side item use,
 * so there is nothing to send the other way.
 */
public final class RCNetwork {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ClassIds.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private RCNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, S2CClassStateSync.class, S2CClassStateSync::encode, S2CClassStateSync::decode,
                S2CClassStateSync::handle, Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CClassDefinitionsSync.class, S2CClassDefinitionsSync::encode,
                S2CClassDefinitionsSync::decode, S2CClassDefinitionsSync::handle,
                Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncState(ServerPlayer player) {
        ClassState state = RCCapabilities.get(player);
        if (state == null || player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CClassStateSync(state.serializeNBT()));
    }

    public static void syncDefinitions(ServerPlayer player) {
        if (player.connection == null) return;
        ClassDataManager.Snapshot snapshot = ClassDataManager.server();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CClassDefinitionsSync(snapshot.rawJson(), snapshot.revision()));
    }
}
