package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicskills.common.util.ItemBonusTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Artificer (Intelligence / Tinkering) — Infusions. Keen Edge stamps Runic Skills' own bonus-durability
 * tag, which Runic Skills' {@code getMaxDamage} hook reads and which never stacks additively with
 * another stamp (the larger one wins, Runic Skills' rule).
 */
public final class ArtificerPerks implements ClassModule {

    static final String KEEN_EDGE = "cls_artificer_keen_edge";
    static final String OVERCLOCK = "cls_artificer_overclock";
    static final String MASTER_INFUSER = "cls_artificer_master_infuser";
    // Field Notes, Tinker's Eye and Steady Hands are attributes (PerkAttributes).

    @Override
    public void crafted(ServerPlayer member, ItemStack crafted) {
        if (crafted.isEmpty() || !crafted.isDamageableItem() || crafted.getItem() instanceof ArmorItem) return;
        if (Perks.on(member, KEEN_EDGE)) {
            ItemBonusTags.stamp(crafted, ItemBonusTags.BONUS_DURABILITY, (int) Math.round(Perks.v(KEEN_EDGE, 0)));
        }
        if (Perks.on(member, MASTER_INFUSER) && Enchantments.UNBREAKING.canEnchant(crafted)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, crafted) == 0
                && member.getRandom().nextDouble() < Perks.pct(MASTER_INFUSER, 0)) {
            crafted.enchant(Enchantments.UNBREAKING, 1);
        }
    }

    @Override
    public void breakSpeed(ServerPlayer member, PlayerEvent.BreakSpeed event) {
        if (Perks.on(member, OVERCLOCK)) event.setNewSpeed(event.getNewSpeed() * (float) (1.0 + Perks.pct(OVERCLOCK, 0)));
    }
}
