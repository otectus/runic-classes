package com.otectus.runicclasses.kit;

import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.registry.RegistryTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared combat vocabulary for class effects: what kind of hit this is, who counts as an ally or an
 * enemy, and how a class effect deals damage of its own.
 *
 * <p><b>Allies</b> are the member, other players (unless both sides are on different scoreboard teams)
 * and the member's own tamed animals. <b>Enemies</b> are hostile mobs and any mob currently targeting
 * the member or an ally; other players and neutral animals are never caught by a class area effect, so
 * a co-op server cannot grief with one.
 *
 * <p>Damage a class effect deals itself goes through Runic Skills' {@link DamageContext}: it is a
 * secondary hit, so no outgoing table (Runic Skills' or ours) scales it again and it cannot trigger
 * another secondary hit.
 */
public final class Combat {

    /** Chestplate defence at or above which a chestplate counts as iron or heavier (iron is 6). */
    public static final int IRON_CHEST_DEFENSE = 6;

    private Combat() {}

    public static boolean isMelee(DamageSource source, Entity attacker) {
        return source.getEntity() == attacker && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !isMagic(source);
    }

    public static boolean isProjectile(DamageSource source, Entity attacker) {
        return source.getEntity() == attacker && source.is(DamageTypeTags.IS_PROJECTILE);
    }

    /** Magic as Runic Skills' Magic Resist understands it (a pack-extensible tag), plus vanilla's magic types. */
    public static boolean isMagic(DamageSource source) {
        return source.is(RegistryTags.DamageTypes.AFFECTED_BY_MAGIC_RESISTANCE)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    public static boolean unarmed(Player player) {
        return player.getMainHandItem().isEmpty();
    }

    public static boolean isUndead(LivingEntity entity) {
        return entity.getMobType() == MobType.UNDEAD;
    }

    /** No chestplate, or one lighter than iron (leather, gold, chainmail). Non-armour chest items count as none. */
    public static boolean lightChest(Player player) {
        ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        return !(chest.getItem() instanceof ArmorItem armor) || armor.getDefense() < IRON_CHEST_DEFENSE;
    }

    /** Every armour piece lighter than iron for its slot (iron's defence: 2, 5, 6, 2). */
    public static boolean noHeavyArmor(Player player) {
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.getItem() instanceof ArmorItem armor) {
                int iron = switch (armor.getEquipmentSlot()) {
                    case HEAD, FEET -> 2;
                    case LEGS -> 5;
                    case CHEST -> 6;
                    default -> Integer.MAX_VALUE;
                };
                if (armor.getDefense() >= iron) return false;
            }
        }
        return true;
    }

    public static boolean isAlly(Player member, @Nullable Entity other) {
        if (other == null || !other.isAlive()) return false;
        if (other == member) return true;
        if (other instanceof Player player) {
            if (player.isSpectator()) return false;
            return member.getTeam() == null || player.getTeam() == null || member.isAlliedTo(player);
        }
        return other instanceof TamableAnimal pet && pet.isTame() && member.getUUID().equals(pet.getOwnerUUID());
    }

    public static boolean isEnemy(Player member, @Nullable Entity other) {
        if (!(other instanceof LivingEntity living) || !living.isAlive() || other instanceof ArmorStand) return false;
        if (other instanceof Player || isAlly(member, other)) return false;
        if (other instanceof Enemy) return true;
        return other instanceof Mob mob && mob.getTarget() != null && isAlly(member, mob.getTarget());
    }

    /** Allies within {@code radius} of {@code center}, the member included when {@code includeSelf}. */
    public static List<LivingEntity> alliesNear(ServerPlayer member, Vec3 center, double radius, boolean includeSelf) {
        AABB box = new AABB(center, center).inflate(radius);
        double r2 = radius * radius;
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : member.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == member && !includeSelf) continue;
            if (e.distanceToSqr(center) <= r2 && isAlly(member, e)) out.add(e);
        }
        return out;
    }

    public static List<LivingEntity> enemiesNear(ServerPlayer member, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        double r2 = radius * radius;
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : member.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e.distanceToSqr(center) <= r2 && isEnemy(member, e)) out.add(e);
        }
        return out;
    }

    public static boolean isPetOf(Entity entity, Player owner) {
        return entity instanceof TamableAnimal pet && pet.isTame() && owner.getUUID().equals(pet.getOwnerUUID());
    }

    public static boolean isAnimalOrArthropod(@Nullable Entity entity) {
        return entity instanceof Animal || (entity instanceof LivingEntity l && l.getMobType() == MobType.ARTHROPOD);
    }

    /**
     * Deals class-effect damage as a Runic secondary hit. Does nothing when the current hit is itself
     * a secondary one (no chains) or the nesting ceiling is reached.
     *
     * @return whether the hit was dealt
     */
    public static boolean secondaryHurt(ServerPlayer owner, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !target.isAlive() || !DamageContext.mayEmitSecondary()) return false;
        try (DamageContext.Scope scope = DamageContext.push(owner.getUUID(), DamageContext.Origin.OTHER_RUNIC_SECONDARY)) {
            if (scope.isSuppressed()) return false;
            target.invulnerableTime = 0;
            return target.hurt(source, amount);
        }
    }

    public static boolean inCombat(ServerPlayer player, long now) {
        return now - CombatMemory.of(player).lastCombatTick <= 100;
    }
}
