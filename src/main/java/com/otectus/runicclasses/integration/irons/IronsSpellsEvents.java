package com.otectus.runicclasses.integration.irons;

import com.otectus.runicclasses.kit.KitDispatcher;
import com.otectus.runicclasses.kit.SpellCast;
import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;

/**
 * The typed half of the Iron's Spells bridge (compiled against 1.20.1-3.16.3, the file Runic Skills
 * uses). Only ever loaded by name from {@link IronsSpellsBridge#init()} after the mod is confirmed.
 */
public final class IronsSpellsEvents implements IronsSpellsBridge.Impl {

    private IronsSpellsEvents() {}

    public static void install() {
        IronsSpellsEvents events = new IronsSpellsEvents();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, events::onCast);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, events::onModifyLevel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, events::onHeal);
        IronsSpellsBridge.install(events);
    }

    private void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpellCast cast = new SpellCast(event.getSpellId(), event.getSpellLevel(), event.getManaCost());
        KitDispatcher.spellCast(player, cast);
        if (cast.level() != event.getSpellLevel()) event.setSpellLevel(cast.level());
        if (cast.manaCost() != event.getManaCost()) event.setManaCost(cast.manaCost());
    }

    private void onModifyLevel(ModifySpellLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int bonus = KitDispatcher.spellLevelBonus(player);
        if (bonus > 0) event.addLevels(bonus);
    }

    private void onHeal(SpellHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer caster) || event.getTargetEntity() == null) return;
        KitDispatcher.spellHealed(caster, event.getTargetEntity());
    }

    @Override
    public boolean addManaFraction(ServerPlayer player, double fraction) {
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data == null || fraction <= 0) return false;
        double max = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float target = (float) Math.min(max, data.getMana() + max * fraction);
        if (target <= data.getMana()) return false;
        data.setMana(target);
        return true;
    }

    @Override
    public boolean refundCooldown(ServerPlayer player, String spellId, double fraction) {
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data == null || spellId == null || fraction <= 0) return false;
        PlayerCooldowns cooldowns = data.getPlayerCooldowns();
        CooldownInstance instance = cooldowns.getSpellCooldowns().get(spellId);
        if (instance == null) return false;
        int refund = (int) Math.floor(instance.getSpellCooldown() * Math.min(1.0, fraction));
        if (refund <= 0) return false;
        cooldowns.decrementCooldown(instance, refund);
        cooldowns.syncToPlayer(player);
        return true;
    }

    @Override
    @Nullable
    public String spellIdOf(DamageSource source) {
        return source instanceof SpellDamageSource spellSource && spellSource.spell() != null
                ? spellSource.spell().getSpellId() : null;
    }
}
