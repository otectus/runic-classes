package com.otectus.runicclasses.service;

import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.config.RCServerConfig;
import com.otectus.runicclasses.data.ClassDefinition;
import com.otectus.runicclasses.integration.treasurebags.TreasureBagsBridge;
import com.otectus.runicclasses.state.ClassState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * The one-time starter bag (docs/plan.md 2.9).
 *
 * <p>Treasure Bags' universal spawn bag is granted by Silent Lib at {@code PlayerLoggedInEvent}, before
 * heritage, race or class exist, so a class bag cannot be chosen there. The grant moves to the moment
 * the class layer is first confirmed, Classless included, and the pack's login table becomes empty.
 */
public final class StarterBagService {

    /** Silent Lib's record of spawn items given: {@code PlayerPersisted -> silentlib.SpawnItemsGiven}. */
    private static final String SILENT_LIB_COMPOUND = "silentlib.SpawnItemsGiven";
    /** The key Silent Lib writes for Treasure Bags' {@code treasurebags:starting_inventory} grant. */
    private static final String LEGACY_GRANT_KEY = "treasurebags.starting_inventory";

    private StarterBagService() {}

    /**
     * Grants the bag for the first confirmed class if nothing was granted yet.
     *
     * @return whether the state changed
     */
    static boolean maybeGrant(ServerPlayer player, ClassState state, ClassHelper.Selection selection) {
        if (state.starterBagGranted) return false;
        ClassDefinition def = selection.definition();
        if (def == null) return false;
        if (state.firstClass.isEmpty()) state.firstClass = def.id().toString();
        if (receivedLegacyBag(player)) {
            // A player from before RunicClasses already got the Newcomer's Knapsack at some login.
            state.starterBagGranted = true;
            state.starterBagClass = "legacy";
            ClassReconciler.log("{} received the universal bag before RunicClasses; no class bag.", player.getGameProfile().getName());
            return true;
        }
        if (def.isClassless() && !RCServerConfig.get(RCServerConfig.CLASSLESS_GETS_UNIVERSAL_BAG, true)) {
            // Deferred: the first real class gets its bag instead.
            return true;
        }
        give(player, def);
        state.starterBagGranted = true;
        state.starterBagClass = def.id().toString();
        player.displayClientMessage(Component.translatable("message.runicclasses.bag.granted"), false);
        RunicClasses.LOGGER.info("Granted the {} starter bag to {}.", def.id(), player.getGameProfile().getName());
        return true;
    }

    /** Puts the class's bag (or, without Treasure Bags, its contents) into the player's inventory. */
    public static int give(ServerPlayer player, ClassDefinition def) {
        List<ItemStack> stacks = roll(player, def);
        for (ItemStack stack : stacks) ItemHandlerHelper.giveItemToPlayer(player, stack);
        return stacks.size();
    }

    static List<ItemStack> roll(ServerPlayer player, ClassDefinition def) {
        ClassDefinition.StarterBag bag = def.starterBag();
        if (bag == null) return List.of();
        if (bag.bagType() != null) {
            ItemStack wrapped = TreasureBagsBridge.bagOf(bag.bagType());
            if (!wrapped.isEmpty()) return List.of(wrapped);
        }
        ServerLevel level = player.serverLevel();
        LootTable table = level.getServer().getLootData().getLootTable(bag.lootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .create(LootContextParamSets.GIFT);
        return table.getRandomItems(params);
    }

    /** Whether Silent Lib recorded the universal spawn bag for this player at an earlier login. */
    public static boolean receivedLegacyBag(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getCompound(SILENT_LIB_COMPOUND).getBoolean(LEGACY_GRANT_KEY);
    }
}
