package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.config.RCServerConfig;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.registry.RCItems;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;

/** The Class Orb (docs/plan.md 2.3): never mid-selection, consumed in survival, kept in creative, cooldown honoured. */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClassOrbGameTest {

    private static final String EMPTY = "empty";

    private static InteractionResultHolder<ItemStack> use(ServerPlayer player) {
        ItemStack orb = new ItemStack(RCItems.CLASS_ORB.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, orb);
        return orb.use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = EMPTY)
    public static void refusedMidSelection(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "orb_mid_selection");
        TestPlayers.completeOtherLayers(player);
        use(player);
        expect(player.getMainHandItem().getCount() == 1, "an orb used before a class is chosen must not be consumed");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void consumedInSurvivalAndClearsTheClass(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "orb_survival");
        player.setGameMode(GameType.SURVIVAL);
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        use(player);
        expect(player.getMainHandItem().isEmpty(), "the orb must be consumed in survival");
        expect(!TestPlayers.container(player).hasOrigin(ClassIds.CLASS_LAYER), "the class layer must be emptied");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && !state.hasAppliedClass(), "the player is Classless until they choose again");
        expect(TestPlayers.container(player).getOrigins().size() > 0, "the other layers must be untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void keptInCreative(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "orb_creative");
        player.setGameMode(GameType.CREATIVE);
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "druid");
        use(player);
        expect(player.getMainHandItem().getCount() == 1, "the orb must be kept in creative");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cooldownIsHonoured(GameTestHelper helper) {
        int previous = RCServerConfig.REPICK_COOLDOWN_TICKS.get();
        RCServerConfig.REPICK_COOLDOWN_TICKS.set(1200);
        try {
            ServerPlayer player = TestPlayers.connected(helper, "orb_cooldown");
            player.setGameMode(GameType.SURVIVAL);
            TestPlayers.completeOtherLayers(player);
            TestPlayers.choose(player, "bard");
            use(player);
            TestPlayers.choose(player, "bard");
            use(player);
            expect(player.getMainHandItem().getCount() == 1, "a second orb inside the cooldown must be refused");
            expect(TestPlayers.container(player).hasOrigin(ClassIds.CLASS_LAYER), "a refused orb must not empty the layer");
        } finally {
            RCServerConfig.REPICK_COOLDOWN_TICKS.set(previous);
        }
        helper.succeed();
    }
}
