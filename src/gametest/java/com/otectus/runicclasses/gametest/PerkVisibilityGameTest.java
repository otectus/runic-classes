package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.ContentGateHooks;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skills;

/** Class Perks are invisible and inert for anyone who is not of the class (docs/plan.md 2.7). */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerkVisibilityGameTest {

    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public static void aRogueCannotSeeOrUseFighterPerks(GameTestHelper helper) {
        ServerPlayer rogue = TestPlayers.connected(helper, "visibility_rogue");
        TestPlayers.completeOtherLayers(rogue);
        TestPlayers.choose(rogue, "rogue");
        Perk drill = RCPerks.get("cls_fighter_weapon_drill");
        Perk backstab = RCPerks.get("cls_rogue_backstab");
        expect(!ContentGateHooks.perkAvailable(rogue, drill), "a Rogue must not see Weapon Drill");
        expect(ContentGateHooks.perkAvailable(rogue, backstab), "a Rogue must see Backstab");
        SkillCapability cap = skills(rogue);
        cap.setSkillLevel(drill.getSkill(), 32);
        cap.setPerkRank(drill, 1);
        expect(!drill.isEnabled(rogue), "a rank set on a foreign class perk must still do nothing");
        expect(backstab.isEnabled(rogue), "the Rogue's own starting perk must be in effect");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ordinaryPerksAreUntouched(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.connected(helper, "visibility_ordinary");
        TestPlayers.completeOtherLayers(player);
        TestPlayers.choose(player, "classless");
        int checked = 0;
        for (Perk perk : RegistryPerks.getCachedValues()) {
            if (!"runicskills".equals(perk.getMod())) continue;
            expect(ContentGateHooks.perkAvailable(player, perk), "Runic Skills perk " + perk.getName() + " must stay available");
            checked++;
        }
        expect(checked > 100, "expected the Runic Skills catalogue, saw " + checked);
        helper.succeed();
    }
}
