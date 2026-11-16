package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.SkillCapHooks;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skill;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skills;

/** Re-picking is a wholesale swap (docs/plan.md 2.0.1): stats, caps, perks and powers all follow the new class. */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClassSwapGameTest {

    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public static void fighterToBarbarianSwapsEverything(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "swap_fighter_barbarian");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        SkillCapability cap = skills(player);
        cap.setPerkRank(RCPerks.get("cls_fighter_riposte"), 1);
        cap.equipPower(RCPowers.get("cls_fighter_measured_cut"));
        TestPlayers.choose(player, "barbarian");
        int base = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        expect(cap.getSkillLevel(skill("strength")) == 8, "Strength is shared: back to 8");
        expect(cap.getSkillLevel(skill("endurance")) == 1, "Endurance leaves with the Fighter");
        expect(cap.getSkillLevel(skill("constitution")) == 8, "Constitution arrives with the Barbarian");
        expect(SkillCapHooks.skillCap(player, skill("endurance")) == base, "the Endurance raise must go");
        expect(SkillCapHooks.skillCap(player, skill("constitution")) == base + 8, "the Constitution raise must arrive");
        expect(cap.getPerkRank(RCPerks.get("cls_fighter_riposte")) == 0, "Fighter perks must reset");
        expect(!cap.isPowerEquipped(RCPowers.get("cls_fighter_measured_cut")), "Fighter powers must leave their slots");
        expect(cap.getPerkRank(RCPerks.get("cls_barbarian_bloodied_resolve")) == 1, "Barbarian starting perks must enable");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && "runicclasses:barbarian".equals(state.appliedClass), "the record must name the Barbarian");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void reconcilingTwiceChangesNothing(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "swap_idempotent");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "ranger");
        SkillCapability cap = skills(player);
        int dex = cap.getSkillLevel(skill("dexterity"));
        cap.setPerkRank(RCPerks.get("cls_ranger_steady_draw"), 0);
        com.otectus.runicclasses.service.ClassReconciler.reconcile(player);
        com.otectus.runicclasses.service.ClassReconciler.reconcile(player);
        expect(cap.getSkillLevel(skill("dexterity")) == dex, "a repeated reconcile must not grant again");
        expect(cap.getPerkRank(RCPerks.get("cls_ranger_steady_draw")) == 0, "a player's own toggle must be left alone");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anEmptiedLayerIsClassless(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "swap_emptied");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "monk");
        TestPlayers.clearClass(player);
        ClassState state = RCCapabilities.get(player);
        expect(state != null && !state.hasAppliedClass(), "an empty class layer must leave no grants applied");
        expect(skills(player).getSkillLevel(skill("dexterity")) == 1, "Dexterity must return to 1");
        helper.succeed();
    }
}
