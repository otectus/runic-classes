package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassDataManager;
import com.otectus.runicclasses.data.ClassDefinitionValidator;
import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;

/**
 * Runic Skills keys several caches by path alone, so a path collision across namespaces would throw at
 * the first lookup (docs/plan.md risks). Also proves the registration order: the memoised registry
 * snapshot was taken after RunicClasses registered, so all 78 Perks and 39 Powers are in it.
 */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class RegistryUniquenessGameTest {

    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public static void pathsAreUniqueAndEveryClassEntryIsListed(GameTestHelper helper) {
        Set<String> perkPaths = new HashSet<>();
        int classPerks = 0;
        for (Perk perk : RegistryPerks.getCachedValues()) {
            expect(perkPaths.add(perk.getName()), "duplicate perk path " + perk.getName());
            if (ClassIds.MOD_ID.equals(perk.getMod())) {
                classPerks++;
                expect(RegistryPerks.getPerk(perk.getName()) == perk, "path lookup must find " + perk.getName());
            }
        }
        expect(classPerks == 78, "expected 78 class perks in the snapshot, found " + classPerks);
        Set<String> powerPaths = new HashSet<>();
        int classPowers = 0;
        for (Power power : RegistryPowers.getCachedValues()) {
            expect(powerPaths.add(power.getName()), "duplicate power path " + power.getName());
            if (ClassIds.MOD_ID.equals(power.getMod())) {
                classPowers++;
                expect(RegistryPowers.byId(power.key) == power, "byId must resolve " + power.key);
            }
        }
        expect(classPowers == 39, "expected 39 class powers in the snapshot, found " + classPowers);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theShippedDataIsValid(GameTestHelper helper) {
        var snapshot = ClassDataManager.server();
        expect(snapshot.byId().size() == 14, "expected 13 classes and Classless, found " + snapshot.byId().size());
        expect(!ClassDefinitionValidator.hasErrors(ClassDefinitionValidator.validate(snapshot.byId())), "the shipped class data has errors");
        var origins = ClassDefinitionValidator.validateOrigins(snapshot.byId());
        expect(origins.isEmpty(), "Origins problems: " + origins);
        helper.succeed();
    }
}
