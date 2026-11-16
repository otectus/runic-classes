package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.registry.RCPowers;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skill;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skills;

/**
 * Class Powers ride Runic Skills' own ladder (docs/plan.md 2.8, OQ-7: no exemptions): the class Seal
 * needs the class Mark equipped and the Intelligence gate, and nobody outside the class may equip any.
 */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerLadderGameTest {

    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public static void theSealNeedsTheMarkAndIntelligence(GameTestHelper helper) {
        ServerPlayer fighter = TestPlayers.connected(helper, "ladder_fighter");
        TestPlayers.completeOtherLayers(fighter);
        TestPlayers.choose(fighter, "fighter");
        SkillCapability cap = skills(fighter);
        Power mark = RCPowers.get("cls_fighter_measured_cut");
        Power seal = RCPowers.get("cls_fighter_bulwark_call");
        cap.setSkillLevel(skill("strength"), PowerEligibility.governingSkillRequirement(PowerTier.SEAL));
        int intelligence = PowerEligibility.secondarySkillRequirement(PowerTier.SEAL);
        if (intelligence > 1) {
            cap.setSkillLevel(skill("intelligence"), 1);
            expect(PowerEligibility.evaluateEquip(fighter, seal).reason() == PowerEligibility.Reason.SECONDARY_SKILL_TOO_LOW,
                    "the Intelligence gate applies to class Seals");
            cap.setSkillLevel(skill("intelligence"), intelligence);
        }
        expect(PowerEligibility.evaluateEquip(fighter, seal).reason() == PowerEligibility.Reason.MISSING_PREREQUISITE,
                "the class Seal needs the class Mark equipped, got " + PowerEligibility.evaluateEquip(fighter, seal).reason());
        expect(PowerEligibility.evaluateEquip(fighter, mark).eligible(), "the Mark should be equippable at the Seal level");
        cap.equipPower(mark);
        PowerEligibility.Reason after = PowerEligibility.evaluateEquip(fighter, seal).reason();
        expect(after == PowerEligibility.Reason.ELIGIBLE || after == PowerEligibility.Reason.INSUFFICIENT_POWER_POINTS,
                "with the Mark equipped the chain is satisfied, got " + after);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void otherClassesAreRestricted(GameTestHelper helper) {
        ServerPlayer cleric = TestPlayers.connected(helper, "ladder_cleric");
        TestPlayers.completeOtherLayers(cleric);
        TestPlayers.choose(cleric, "cleric");
        skills(cleric).setSkillLevel(skill("strength"), 32);
        Power fighterMark = RCPowers.get("cls_fighter_measured_cut");
        expect(PowerEligibility.evaluateEquip(cleric, fighterMark).reason() == PowerEligibility.Reason.CLASS_RESTRICTED,
                "a Cleric must be refused a Fighter Power");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gatesFollowTheRunicSkillsTiers(GameTestHelper helper) {
        for (var entry : RCPowers.all().entrySet()) {
            Power power = entry.getValue().get();
            expect(power.requiredSkillLevel == PowerEligibility.governingSkillRequirement(power.getTier()),
                    entry.getKey() + " gate " + power.requiredSkillLevel + " differs from the tier gate");
            expect(ClassIds.MOD_ID.equals(power.getSchoolId().getNamespace()), entry.getKey() + " must use its class school");
        }
        helper.succeed();
    }
}
