package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Hit;
import com.otectus.runicclasses.kit.Perks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;

/** Fighter (Strength / Endurance) — Drill. */
public final class FighterPerks implements ClassModule {

    public static final String WEAPON_DRILL = "cls_fighter_weapon_drill";
    public static final String QUARTERMASTER = "cls_fighter_quartermaster";
    static final String RIPOSTE = "cls_fighter_riposte";
    static final String BATTLE_RHYTHM = "cls_fighter_battle_rhythm";
    static final String CHAMPION = "cls_fighter_champion";
    // Armor Mastery is an attribute (PerkAttributes); Quartermaster is a Runic Skills wear contributor (Kits).

    /** Swords, axes and spears; packs extend {@code runicclasses:drill_weapons}. */
    public static final TagKey<Item> DRILL_WEAPONS = TagKey.create(Registries.ITEM, new ResourceLocation("runicclasses", "drill_weapons"));

    @Override
    public void outgoing(Hit hit) {
        if (!hit.melee) return;
        ServerPlayer p = hit.member;
        CombatMemory memory = CombatMemory.of(p);
        if (Perks.on(p, WEAPON_DRILL)) {
            ItemStack weapon = p.getMainHandItem();
            if (weapon.is(DRILL_WEAPONS) || weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem) {
                hit.addPercent(Perks.pct(WEAPON_DRILL, 0));
            }
        }
        if (Perks.on(p, RIPOSTE) && memory.consume("riposte", hit.now)) {
            hit.addPercent(Perks.pct(RIPOSTE, 1));
        }
        if (Perks.on(p, BATTLE_RHYTHM)) {
            if (hit.target.getUUID().equals(memory.streakTarget) && hit.now - memory.streakLastTick <= 60) {
                memory.streakCount++;
            } else {
                memory.streakTarget = hit.target.getUUID();
                memory.streakCount = 1;
            }
            memory.streakLastTick = hit.now;
            if (memory.streakCount % 3 == 0) hit.addPercent(Perks.pct(BATTLE_RHYTHM, 0));
        }
    }

    @Override
    public void blocked(ServerPlayer member, ShieldBlockEvent event) {
        if (Perks.on(member, RIPOSTE)) {
            long now = member.level().getGameTime();
            CombatMemory.of(member).open("riposte", now + Perks.ticks(RIPOSTE, 0));
        }
    }

    @Override
    public void crit(ServerPlayer member, CriticalHitEvent event) {
        if (!Perks.on(member, CHAMPION)) return;
        if (event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW) {
            event.setDamageModifier(event.getDamageModifier() + (float) Perks.pct(CHAMPION, 0));
        }
    }
}
