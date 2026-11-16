package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.perk.handlers.ArtificerPerks;
import com.otectus.runicclasses.perk.handlers.BarbarianPerks;
import com.otectus.runicclasses.perk.handlers.BardPerks;
import com.otectus.runicclasses.perk.handlers.ClericPerks;
import com.otectus.runicclasses.perk.handlers.DruidPerks;
import com.otectus.runicclasses.perk.handlers.FighterPerks;
import com.otectus.runicclasses.perk.handlers.MonkPerks;
import com.otectus.runicclasses.perk.handlers.PaladinPerks;
import com.otectus.runicclasses.perk.handlers.RangerPerks;
import com.otectus.runicclasses.perk.handlers.RoguePerks;
import com.otectus.runicclasses.perk.handlers.SorcererPerks;
import com.otectus.runicclasses.perk.handlers.WarlockPerks;
import com.otectus.runicclasses.perk.handlers.WizardPerks;
import com.otectus.runicclasses.power.handlers.ArtificerPowers;
import com.otectus.runicclasses.power.handlers.BarbarianPowers;
import com.otectus.runicclasses.power.handlers.BardPowers;
import com.otectus.runicclasses.power.handlers.ClericPowers;
import com.otectus.runicclasses.power.handlers.DruidPowers;
import com.otectus.runicclasses.power.handlers.FighterPowers;
import com.otectus.runicclasses.power.handlers.MonkPowers;
import com.otectus.runicclasses.power.handlers.PaladinPowers;
import com.otectus.runicclasses.power.handlers.RangerPowers;
import com.otectus.runicclasses.power.handlers.RoguePowers;
import com.otectus.runicclasses.power.handlers.SorcererPowers;
import com.otectus.runicclasses.power.handlers.WarlockPowers;
import com.otectus.runicclasses.power.handlers.WizardPowers;
import com.otectus.runicclasses.registry.RCPerks;
import com.otectus.runicskills.common.durability.WearAvoidance;
import net.minecraft.world.item.ArmorItem;

/** Wires every class's Perks and Powers modules into the dispatcher. */
public final class Kits {

    private Kits() {}

    public static void registerAll() {
        pair("barbarian", new BarbarianPerks(), new BarbarianPowers());
        pair("fighter", new FighterPerks(), new FighterPowers());
        pair("monk", new MonkPerks(), new MonkPowers());
        pair("rogue", new RoguePerks(), new RoguePowers());
        pair("ranger", new RangerPerks(), new RangerPowers());
        pair("bard", new BardPerks(), new BardPowers());
        pair("cleric", new ClericPerks(), new ClericPowers());
        pair("druid", new DruidPerks(), new DruidPowers());
        pair("sorcerer", new SorcererPerks(), new SorcererPowers());
        pair("warlock", new WarlockPerks(), new WarlockPowers());
        pair("wizard", new WizardPerks(), new WizardPowers());
        pair("artificer", new ArtificerPerks(), new ArtificerPowers());
        pair("paladin", new PaladinPerks(), new PaladinPowers());
    }

    private static void pair(String classPath, ClassModule perks, ClassModule powers) {
        KitDispatcher.register(classPath, perks);
        KitDispatcher.register(classPath, powers);
    }

    /** Hooks that need Runic Skills' setup to have run. */
    public static void commonSetup() {
        // Quartermaster joins Runic Skills' single wear-avoidance sum, under its one 90% clamp.
        WearAvoidance.addContributor((user, stack) -> stack.getItem() instanceof ArmorItem
                && RCPerks.on(user, FighterPerks.QUARTERMASTER) ? RCPerks.value(FighterPerks.QUARTERMASTER, 0) / 100.0 : 0.0);
    }
}
