package com.otectus.runicclasses.state;

import com.otectus.runicclasses.data.ClassIds;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import javax.annotation.Nullable;

/** {@code runicclasses:class_state}, attached to every real player on both sides. */
public final class RCCapabilities {

    public static final Capability<ClassState> CLASS_STATE = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = ClassIds.id("class_state");

    private RCCapabilities() {}

    /** MOD bus. */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ClassState.class);
    }

    /** FORGE bus. */
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player) || event.getObject() instanceof FakePlayer) return;
        Provider provider = new Provider();
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    @Nullable
    public static ClassState get(@Nullable Player player) {
        if (player == null) return null;
        return player.getCapability(CLASS_STATE).resolve().orElse(null);
    }

    static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final ClassState state = new ClassState();
        private LazyOptional<ClassState> optional = LazyOptional.of(() -> state);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return capability == CLASS_STATE ? optional.cast() : LazyOptional.empty();
        }

        void invalidate() {
            optional.invalidate();
        }

        @Override
        public CompoundTag serializeNBT() {
            return state.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            state.deserializeNBT(tag);
        }
    }
}
