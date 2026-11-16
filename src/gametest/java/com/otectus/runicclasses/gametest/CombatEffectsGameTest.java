package com.otectus.runicclasses.gametest;

import com.otectus.runicclasses.data.ClassIds;
import com.otectus.runicclasses.kit.PerkAttributes;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

import static com.otectus.runicclasses.gametest.ClassGrantGameTest.expect;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skill;
import static com.otectus.runicclasses.gametest.ClassGrantGameTest.skills;

/**
 * Class effects end to end through the dispatcher: real damage events, real attribute modifiers, real
 * effect application and a real lethal hit. One representative per hook family.
 */
@GameTestHolder(ClassIds.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatEffectsGameTest {

    private static final String EMPTY = "empty";

    /** Health a fresh zombie loses to one melee hit of {@code amount} from {@code player}. */
    private static float hitZombie(GameTestHelper helper, ServerPlayer player, float amount) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.setHealth(zombie.getMaxHealth());
        float before = zombie.getHealth();
        zombie.invulnerableTime = 0;
        zombie.hurt(player.damageSources().playerAttack(player), amount);
        float lost = before - zombie.getHealth();
        zombie.discard();
        return lost;
    }

    @GameTest(template = EMPTY)
    public static void weaponDrillRaisesSwordDamage(GameTestHelper helper) {
        ServerPlayer plain = TestPlayers.connected(helper, "combat_plain");
        TestPlayers.completeOtherLayers(plain);
        TestPlayers.choose(plain, "classless");
        plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        ServerPlayer fighter = TestPlayers.connected(helper, "combat_fighter");
        TestPlayers.completeOtherLayers(fighter);
        TestPlayers.choose(fighter, "fighter");
        fighter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        float base = hitZombie(helper, plain, 10.0f);
        float drilled = hitZombie(helper, fighter, 10.0f);
        expect(drilled > base * 1.03f && drilled < base * 1.09f, "Weapon Drill should add about 6%: " + base + " -> " + drilled);
        helper.succeed();
    }

    /**
     * Another mod's +200% on the same hit (here a listener at normal priority, where Runic Skills scales
     * damage): Weapon Drill's 6% adds to it, x3.06, and never compounds, x3.18 (docs/plan.md 4, OQ-15).
     */
    @GameTest(template = EMPTY)
    public static void classBonusAddsToOtherPercentages(GameTestHelper helper) {
        ServerPlayer plain = TestPlayers.connected(helper, "stack_plain");
        TestPlayers.completeOtherLayers(plain);
        TestPlayers.choose(plain, "classless");
        plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        ServerPlayer fighter = TestPlayers.connected(helper, "stack_fighter");
        TestPlayers.completeOtherLayers(fighter);
        TestPlayers.choose(fighter, "fighter");
        fighter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        Consumer<LivingHurtEvent> otherMod = event -> {
            if (event.getSource().getEntity() instanceof ServerPlayer p && p.getGameProfile().getName().startsWith("stack_")) {
                event.setAmount(event.getAmount() * 3.0f);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, otherMod);
        try {
            float base = hitZombie(helper, plain, 4.0f);
            float drilled = hitZombie(helper, fighter, 4.0f);
            expect(drilled > base * 1.01f && drilled < base * 1.04f,
                    "Weapon Drill should add 6% of the unscaled hit (x1.02 here), not compound (x1.06): " + base + " -> " + drilled);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(otherMod);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void openHandAddsFlatUnarmedDamage(GameTestHelper helper) {
        ServerPlayer monk = TestPlayers.connected(helper, "combat_monk");
        TestPlayers.completeOtherLayers(monk);
        TestPlayers.choose(monk, "monk");
        ServerPlayer plain = TestPlayers.connected(helper, "combat_plain_unarmed");
        TestPlayers.completeOtherLayers(plain);
        TestPlayers.choose(plain, "classless");
        // The starter bag's first item lands in the selected hotbar slot; empty both hands to test unarmed.
        monk.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        plain.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float base = hitZombie(helper, plain, 4.0f);
        float palm = hitZombie(helper, monk, 4.0f);
        expect(palm > base + 1.5f, "Open Hand should add about 3 before armour: " + base + " -> " + palm);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thickHideRaisesMaxHealthAndLeavesWithTheClass(GameTestHelper helper) {
        ServerPlayer barbarian = TestPlayers.connected(helper, "combat_thick_hide");
        TestPlayers.completeOtherLayers(barbarian);
        TestPlayers.choose(barbarian, "classless");
        float base = barbarian.getMaxHealth();
        TestPlayers.choose(barbarian, "barbarian");
        PerkAttributes.refresh(barbarian);
        expect(barbarian.getMaxHealth() == base + 2.0f, "Thick Hide should add 2 max health: " + base + " -> " + barbarian.getMaxHealth());
        TestPlayers.choose(barbarian, "classless");
        expect(barbarian.getMaxHealth() == base, "the modifier must leave with the class, got " + barbarian.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void nightSightBlocksBlindness(GameTestHelper helper) {
        ServerPlayer warlock = TestPlayers.connected(helper, "combat_night_sight");
        TestPlayers.completeOtherLayers(warlock);
        TestPlayers.choose(warlock, "warlock");
        warlock.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        expect(!warlock.hasEffect(MobEffects.BLINDNESS), "a Warlock with Night Sight must not be blinded");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void titansFurySurvivesOneLethalBlow(GameTestHelper helper) {
        ServerPlayer barbarian = TestPlayers.connected(helper, "combat_titans_fury");
        TestPlayers.completeOtherLayers(barbarian);
        TestPlayers.choose(barbarian, "barbarian");
        SkillCapability cap = skills(barbarian);
        cap.setSkillLevel(skill("constitution"), 36);
        cap.setPerkRank(RCPerks.get("cls_barbarian_titans_fury"), 1);
        // A freshly built ServerPlayer carries 60 ticks of spawn protection; this test needs the hit to land.
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, barbarian, 0, "f_8921_");
        barbarian.invulnerableTime = 0;
        barbarian.hurt(barbarian.damageSources().generic(), 1000.0f);
        expect(barbarian.isAlive() && barbarian.getHealth() == 1.0f, "Titan's Fury should leave the Barbarian at 1 health");
        expect(barbarian.hasEffect(MobEffects.DAMAGE_BOOST), "Titan's Fury grants Strength II");
        barbarian.invulnerableTime = 0;
        barbarian.hurt(barbarian.damageSources().generic(), 1000.0f);
        expect(!barbarian.isAlive(), "the shared survival lockout must stop a second rescue");
        helper.succeed();
    }
}
