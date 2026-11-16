package com.otectus.runicclasses.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What RunicClasses has applied to one player.
 *
 * <p>Which class the player <em>is</em> lives in the Origins container (the class layer). This records
 * only what has been <em>granted</em>, so reconciling is a diff and every grant can be undone exactly,
 * with the numbers it was made with, even after the class definitions change underneath it.
 *
 * <p>{@code grantPrimary}/{@code grantSecondary} are the free levels actually given: a player already
 * at Strength 12 who becomes a Fighter receives none for Strength, and loses none on leaving. Undo
 * subtracts exactly these, never {@code startLevel - 1} blindly, so purchased levels always survive.
 */
public final class ClassState implements INBTSerializable<CompoundTag> {

    public static final int DATA_VERSION = 1;
    /** Wizard's Studied Strike remembers at most this many kinds of creature. */
    public static final int MAX_STUDIED_TYPES = 64;

    /** Applied class id ({@code runicclasses:fighter}); empty when none (Classless or unchosen). */
    public String appliedClass = "";
    public String appliedPrimary = "";
    public String appliedSecondary = "";
    public int appliedStartLevel;
    public int appliedCapBonus;
    public int grantPrimary;
    public int grantSecondary;
    /** Class Perk and Power paths granted with the applied class, for an exact undo. */
    public final List<String> appliedPerks = new ArrayList<>();
    public final List<String> appliedPowers = new ArrayList<>();

    /** First class ever confirmed, Classless included. */
    public String firstClass = "";
    public boolean starterBagGranted;
    /** Which bag was granted: a class id, {@code legacy} for a pre-RunicClasses player, or empty. */
    public String starterBagClass = "";
    /** Server game time of the last Class Orb use; {@code Long.MIN_VALUE} for never. */
    public long lastRepickGameTime = Long.MIN_VALUE;
    /** Entity type ids this player has slain, oldest first (Studied Strike). */
    public final LinkedHashSet<String> studiedTypes = new LinkedHashSet<>();

    public boolean hasAppliedClass() {
        return !this.appliedClass.isEmpty();
    }

    /** The applied class's short name ({@code fighter}), or empty. */
    public String appliedPath() {
        int colon = this.appliedClass.indexOf(':');
        return colon < 0 ? this.appliedClass : this.appliedClass.substring(colon + 1);
    }

    /** Free levels currently granted across both class stats. */
    public int freeLevels() {
        return Math.max(0, this.grantPrimary) + Math.max(0, this.grantSecondary);
    }

    /** Clears the applied grant record (not the bag, first-class or repick history). */
    public void clearApplied() {
        this.appliedClass = "";
        this.appliedPrimary = "";
        this.appliedSecondary = "";
        this.appliedStartLevel = 0;
        this.appliedCapBonus = 0;
        this.grantPrimary = 0;
        this.grantSecondary = 0;
        this.appliedPerks.clear();
        this.appliedPowers.clear();
    }

    public void rememberStudied(String entityTypeId) {
        if (this.studiedTypes.remove(entityTypeId)) {
            this.studiedTypes.add(entityTypeId);
            return;
        }
        this.studiedTypes.add(entityTypeId);
        while (this.studiedTypes.size() > MAX_STUDIED_TYPES) {
            this.studiedTypes.remove(this.studiedTypes.iterator().next());
        }
    }

    public void copyFrom(ClassState other) {
        deserializeNBT(other.serializeNBT());
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("appliedClass", this.appliedClass);
        tag.putString("appliedPrimary", this.appliedPrimary);
        tag.putString("appliedSecondary", this.appliedSecondary);
        tag.putInt("appliedStartLevel", this.appliedStartLevel);
        tag.putInt("appliedCapBonus", this.appliedCapBonus);
        tag.putInt("grantPrimary", this.grantPrimary);
        tag.putInt("grantSecondary", this.grantSecondary);
        tag.put("appliedPerks", strings(this.appliedPerks));
        tag.put("appliedPowers", strings(this.appliedPowers));
        tag.putString("firstClass", this.firstClass);
        tag.putBoolean("starterBagGranted", this.starterBagGranted);
        tag.putString("starterBagClass", this.starterBagClass);
        tag.putLong("lastRepickGameTime", this.lastRepickGameTime);
        tag.put("studiedTypes", strings(this.studiedTypes));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.appliedClass = tag.getString("appliedClass");
        this.appliedPrimary = tag.getString("appliedPrimary");
        this.appliedSecondary = tag.getString("appliedSecondary");
        this.appliedStartLevel = Math.max(0, tag.getInt("appliedStartLevel"));
        this.appliedCapBonus = Math.max(0, tag.getInt("appliedCapBonus"));
        this.grantPrimary = Math.max(0, tag.getInt("grantPrimary"));
        this.grantSecondary = Math.max(0, tag.getInt("grantSecondary"));
        readStrings(tag, "appliedPerks", this.appliedPerks);
        readStrings(tag, "appliedPowers", this.appliedPowers);
        this.firstClass = tag.getString("firstClass");
        this.starterBagGranted = tag.getBoolean("starterBagGranted");
        this.starterBagClass = tag.getString("starterBagClass");
        this.lastRepickGameTime = tag.contains("lastRepickGameTime", Tag.TAG_LONG)
                ? tag.getLong("lastRepickGameTime") : Long.MIN_VALUE;
        List<String> studied = new ArrayList<>();
        readStrings(tag, "studiedTypes", studied);
        this.studiedTypes.clear();
        for (String id : studied) rememberStudied(id);
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(StringTag.valueOf(value));
        return list;
    }

    private static void readStrings(CompoundTag tag, String key, java.util.Collection<String> into) {
        into.clear();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size() && i < 256; i++) into.add(list.getString(i));
    }
}
