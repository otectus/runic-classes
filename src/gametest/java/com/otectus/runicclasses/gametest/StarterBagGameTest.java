package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;

/**
 * The one-time starter bag (docs/plan.md 2.9): granted at the first class confirmation and never again,
 * Classless included (OQ-9), and never to a player Silent Lib already gave the universal bag.
 */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class StarterBagGameTest {

    private static final String EMPTY = "empty";

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) total += stack.getCount();
        return total;
    }

    @GameTest(template = EMPTY)
    public static void theBagArrivesOnceAtTheFirstConfirmation(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "bag_once");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && state.starterBagGranted, "the first confirmation must grant the bag");
        expect("runicclasses:fighter".equals(state.starterBagClass), "the Fighter bag must be the one granted");
        int bread = count(player, Items.BREAD);
        expect(bread > 0 || player.getInventory().items.stream().anyMatch(s -> !s.isEmpty()), "the bag's contents must be in the inventory");
        TestPlayers.choose(player, "barbarian");
        TestPlayers.choose(player, "fighter");
        expect(count(player, Items.BREAD) == bread, "re-picking must never grant another bag");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void classlessUsesUpTheGrant(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "bag_classless");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "classless");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && state.starterBagGranted && "runicclasses:classless".equals(state.starterBagClass),
                "Classless must receive the universal bag and use up the grant");
        int swords = count(player, Items.WOODEN_SWORD);
        TestPlayers.choose(player, "wizard");
        expect(count(player, Items.WOODEN_SWORD) == swords, "a class picked after Classless grants nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aPlayerWhoGotTheUniversalBagGetsNoClassBag(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "bag_legacy");
        CompoundTag persisted = new CompoundTag();
        CompoundTag given = new CompoundTag();
        given.putBoolean("treasurebags.starting_inventory", true);
        persisted.put("silentlib.SpawnItemsGiven", given);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "paladin");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && state.starterBagGranted && "legacy".equals(state.starterBagClass), "the legacy flag must be honoured");
        expect(count(player, Items.BREAD) == 0, "no class bag for a legacy player");
        helper.succeed();
    }
}
