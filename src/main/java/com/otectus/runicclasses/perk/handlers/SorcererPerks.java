package com.otectus.runicclasses.perk.handlers;

import com.otectus.runicclasses.integration.irons.IronsSpellsBridge;
import com.otectus.runicclasses.kit.ClassModule;
import com.otectus.runicclasses.kit.CombatMemory;
import com.otectus.runicclasses.kit.Deferred;
import com.otectus.runicclasses.kit.EffectDurations;
import com.otectus.runicclasses.kit.FinalHit;
import com.otectus.runicclasses.kit.Perks;
import com.otectus.runicclasses.kit.SpellCast;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorcerer (Magic / Constitution) — the Wild Vein. Every spell Perk has a vanilla fallback through
 * thrown potions and effect durations, so the class is whole in a pack without a magic mod.
 */
public final class SorcererPerks implements ClassModule {

    static final String INNATE_RESERVES = "cls_sorcerer_innate_reserves";
    static final String SURGE = "cls_sorcerer_surge";
    static final String VOLATILE_BLOOD = "cls_sorcerer_volatile_blood";
    static final String OVERFLOW = "cls_sorcerer_overflow";
    // Hardy Vessel, Arcane Skin and Innate Reserves' mana are attributes (PerkAttributes).

    /** How long a charged Volatile Blood waits for its spell or potion. */
    private static final long VOLATILE_WINDOW = 20 * 60;

    @Override
    public void selfEffectAdded(ServerPlayer member, MobEffectEvent.Added event) {
        if (IronsSpellsBridge.active() || !Perks.on(member, INNATE_RESERVES)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) {
            EffectDurations.scale(instance, 1.0 + Perks.pct(INNATE_RESERVES, 0));
        }
    }

    @Override
    public void spellCast(ServerPlayer member, SpellCast cast) {
        if (Perks.on(member, SURGE) && member.getRandom().nextDouble() < Perks.pct(SURGE, 0)) cast.makeFree();
        long now = member.level().getGameTime();
        if (Perks.on(member, VOLATILE_BLOOD) && CombatMemory.of(member).consume("volatile_blood", now)) cast.addLevels(1);
    }

    @Override
    public void projectileSpawned(ServerPlayer member, Projectile projectile) {
        if (IronsSpellsBridge.active() || !(projectile instanceof ThrownPotion potion)) return;
        long now = member.level().getGameTime();
        if (Perks.on(member, VOLATILE_BLOOD) && CombatMemory.of(member).consume("volatile_blood", now)) {
            ItemStack stack = potion.getItem();
            List<MobEffectInstance> stronger = new ArrayList<>();
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                stronger.add(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier() + 1,
                        effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
            if (!stronger.isEmpty()) {
                ItemStack copy = stack.copy();
                int colour = PotionUtils.getColor(stack);
                copy.removeTagKey("Potion");
                PotionUtils.setCustomEffects(copy, stronger);
                copy.getOrCreateTag().putInt("CustomPotionColor", colour);
                potion.setItem(copy);
            }
        }
        if (Perks.on(member, SURGE) && member.getRandom().nextDouble() < Perks.pct(SURGE, 0)) {
            ItemStack refund = potion.getItem().copyWithCount(1);
            Deferred.later(() -> ItemHandlerHelper.giveItemToPlayer(member, refund));
        }
    }

    @Override
    public void incomingFinal(FinalHit hit) {
        ServerPlayer p = (ServerPlayer) hit.victim;
        if (Perks.on(p, VOLATILE_BLOOD) && hit.leavesAtOrBelow(Perks.pct(VOLATILE_BLOOD, 0))) {
            CombatMemory.of(p).open("volatile_blood", hit.now + VOLATILE_WINDOW);
        }
    }

    @Override
    public void outgoingFinal(FinalHit hit) {
        if (hit.magic && hit.attacker instanceof ServerPlayer p && Perks.on(p, OVERFLOW)) {
            p.heal(hit.amount() * (float) Perks.pct(OVERFLOW, 0));
        }
    }
}
