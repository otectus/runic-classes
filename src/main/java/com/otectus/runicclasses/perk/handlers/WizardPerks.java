package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicclasses.kit.SpellCast;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * Wizard (Intelligence / Magic) — the Study. Spell Economy's vanilla fallback refunds one level per
 * enchantment: vanilla posts no event when a table enchants, but it always re-rolls the player's
 * enchantment seed, so a seed change while the table is open is an enchantment.
 */
public final class WizardPerks implements ClassModule {

    static final String SPELL_ECONOMY = "cls_wizard_spell_economy";
    static final String PRODIGY = "cls_wizard_prodigy";
    static final String ARCHMAGE = "cls_wizard_archmage";
    // Scholar's Eye, Arcane Focus and Counter-Ward are attributes (PerkAttributes).

    @Override
    public void spellCast(ServerPlayer member, SpellCast cast) {
        if (Perks.on(member, SPELL_ECONOMY)) cast.reduceManaCost(Perks.pct(SPELL_ECONOMY, 0));
    }

    @Override
    public int spellLevelBonus(ServerPlayer member) {
        return Perks.on(member, ARCHMAGE) ? 1 : 0;
    }

    @Override
    public void outgoing(Hit hit) {
        if (hit.magic && !IronsSpellsBridge.active() && Perks.on(hit.member, ARCHMAGE)) hit.addPercent(Perks.pct(ARCHMAGE, 0));
    }

    @Override
    public double levelUpDiscount(Player member, Skill skill) {
        if (!Perks.on(member, PRODIGY) || skill == null) return 0.0;
        boolean studied = skill == RegistrySkills.INTELLIGENCE.get() || skill == RegistrySkills.MAGIC.get();
        return studied ? Perks.pct(PRODIGY, 0) : 0.0;
    }

    @Override
    public void containerOpened(ServerPlayer member, AbstractContainerMenu menu) {
        if (!(menu instanceof EnchantmentMenu)) return;
        CombatMemory memory = CombatMemory.of(member);
        memory.watchingSeed = true;
        memory.watchedSeed = member.getEnchantmentSeed();
    }

    @Override
    public void tick(ServerPlayer member, long now) {
        if (IronsSpellsBridge.active() || !Perks.on(member, SPELL_ECONOMY)) return;
        CombatMemory memory = CombatMemory.of(member);
        if (!(member.containerMenu instanceof EnchantmentMenu)) {
            memory.watchingSeed = false;
            return;
        }
        int seed = member.getEnchantmentSeed();
        if (!memory.watchingSeed) {
            memory.watchingSeed = true;
            memory.watchedSeed = seed;
            return;
        }
        if (seed == memory.watchedSeed) return;
        // The seed moved on while the table was open: an enchantment was just paid for.
        memory.watchedSeed = seed;
        member.giveExperienceLevels(1);
    }
}
