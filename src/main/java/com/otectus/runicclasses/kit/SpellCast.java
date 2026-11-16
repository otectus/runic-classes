package com.otectus.runicclasses.kit;

/**
 * A spell being cast, reduced to plain values so class modules never name Iron's Spells types.
 * {@code integration/irons} fills it from {@code SpellOnCastEvent} and writes the result back.
 */
public final class SpellCast {

    public final String spellId;
    public final int originalLevel;
    public final int originalManaCost;
    private int level;
    private int manaCost;

    public SpellCast(String spellId, int level, int manaCost) {
        this.spellId = spellId;
        this.originalLevel = level;
        this.originalManaCost = manaCost;
        this.level = level;
        this.manaCost = manaCost;
    }

    public int level() {
        return level;
    }

    public int manaCost() {
        return manaCost;
    }

    public void addLevels(int levels) {
        if (levels > 0) this.level += levels;
    }

    /** Scales the mana cost down by {@code fraction} (0.1 = 10% cheaper); never below zero. */
    public void reduceManaCost(double fraction) {
        if (fraction > 0) this.manaCost = (int) Math.max(0, Math.floor(this.manaCost * (1.0 - Math.min(1.0, fraction))));
    }

    public void makeFree() {
        this.manaCost = 0;
    }
}
