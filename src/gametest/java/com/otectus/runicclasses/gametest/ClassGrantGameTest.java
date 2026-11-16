package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicclasses.state.ClassState;
import com.otectus.runicclasses.state.RCCapabilities;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.LevelCaps;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.progression.SkillCapHooks;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Grant and undo arithmetic (docs/plan.md 2.5, 2.6): the two class stats start at 8 with caps raised
 * by 8, starting Perks enable, the global budget grows by exactly the free levels, purchased levels
 * survive a swap, and the level-40 edge loses exactly the one purchased level above the base cap.
 */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClassGrantGameTest {

    private static final String EMPTY = "empty";

    static Skill skill(String name) {
        Skill skill = RegistrySkills.getSkill(name);
        if (skill == null) throw new GameTestAssertException("no Runic Skills Aptitude " + name);
        return skill;
    }

    static SkillCapability skills(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) throw new GameTestAssertException("no Runic Skills capability");
        return cap;
    }

    static void expect(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @GameTest(template = EMPTY)
    public static void fighterStartsAtEightWithRaisedCaps(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "grant_fighter");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        SkillCapability cap = skills(player);
        Skill strength = skill("strength");
        Skill endurance = skill("endurance");
        int base = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        expect(cap.getSkillLevel(strength) == 8, "Strength should start at 8, was " + cap.getSkillLevel(strength));
        expect(cap.getSkillLevel(endurance) == 8, "Endurance should start at 8, was " + cap.getSkillLevel(endurance));
        expect(SkillCapHooks.skillCap(player, strength) == base + 8, "Strength cap should be base + 8");
        expect(SkillCapHooks.skillCap(player, skill("dexterity")) == base, "a non-class stat's cap must not move");
        expect(SkillCapHooks.globalCap(player) == LevelCaps.global() + 14, "the global budget should grow by the 14 free levels");
        expect(cap.getPerkRank(RCPerks.get("cls_fighter_weapon_drill")) == 1, "starting perk Weapon Drill should be enabled");
        expect(cap.getPerkRank(RCPerks.get("cls_fighter_quartermaster")) == 1, "starting perk Quartermaster should be enabled");
        expect(cap.getPerkRank(RCPerks.get("cls_fighter_riposte")) == 0, "earnable perk Riposte must start disabled");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && state.grantPrimary == 7 && state.grantSecondary == 7, "the record should hold 7 + 7 free levels");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void purchasedLevelsSurviveLeavingTheClass(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "grant_purchased");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        Skill strength = skill("strength");
        ProgressionService.setSkillLevel(player, strength, 20, ProgressionService.Cause.COMMAND);
        TestPlayers.choose(player, "classless");
        SkillCapability cap = skills(player);
        expect(cap.getSkillLevel(strength) == 13, "20 with 7 free levels should leave 13, was " + cap.getSkillLevel(strength));
        expect(cap.getSkillLevel(skill("endurance")) == 1, "Endurance should return to 1");
        expect(cap.getPerkRank(RCPerks.get("cls_fighter_weapon_drill")) == 0, "class perks must drop to rank 0");
        expect(SkillCapHooks.globalCap(player) == LevelCaps.global(), "the budget bonus must go with the class");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aStatAtTheRaisedCapReturnsToTheBaseCap(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "grant_edge");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "fighter");
        Skill strength = skill("strength");
        int base = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        ProgressionService.setSkillLevel(player, strength, base + 8, ProgressionService.Cause.COMMAND);
        expect(skills(player).getSkillLevel(strength) == base + 8, "a member can reach base + 8");
        TestPlayers.clearClass(player);
        // base + 8 - 7 = base + 1, clamped to the base cap: exactly one purchased level is lost (OQ-3).
        expect(skills(player).getSkillLevel(strength) == base, "expected the base cap " + base + ", was " + skills(player).getSkillLevel(strength));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anExistingPlayerAboveTheStartLevelGetsNoFreeLevels(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "grant_existing");
        TestPlayers.completeOtherLayers(player);
        Skill strength = skill("strength");
        ProgressionService.setSkillLevel(player, strength, 12, ProgressionService.Cause.COMMAND);
        TestPlayers.choose(player, "barbarian");
        ClassState state = RCCapabilities.get(player);
        expect(skills(player).getSkillLevel(strength) == 12, "a stat already above 8 keeps its level");
        expect(state != null && state.grantPrimary == 0, "no free Strength levels were given, so none are recorded");
        TestPlayers.clearClass(player);
        expect(skills(player).getSkillLevel(strength) == 12, "leaving must not take purchased levels");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void respecRestoresTheStartingLevels(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "grant_respec");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "wizard");
        SkillCapability cap = skills(player);
        for (Skill each : RegistrySkills.getCachedValues()) cap.setSkillLevel(each, 1);
        for (var perk : RCPerks.all().values()) cap.setPerkRank(perk.get(), 0);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.event.RespecEvent(player));
        expect(cap.getSkillLevel(skill("intelligence")) == 8 && cap.getSkillLevel(skill("magic")) == 8, "a respec must give the class levels back");
        expect(cap.getPerkRank(RCPerks.get("cls_wizard_scholars_eye")) == 1, "a respec must re-enable starting perks");
        ClassState state = RCCapabilities.get(player);
        expect(state != null && state.grantPrimary == 7 && state.grantSecondary == 7, "grants must be re-recorded as 7 + 7");
        helper.succeed();
    }
}
