package com.otectus.runicclasses.kit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player runtime state for class effects: combat timing, named windows, counters and per-target
 * bookkeeping. Never saved — a window is a few seconds long — and dropped at logout, at death and when
 * the server stops, the same lifetime Runic Skills gives its own combat memory. Persistent cooldowns
 * live in the Runic Skills capability instead (perk cooldowns and Power debt).
 */
public final class CombatMemory {

    private static final Map<UUID, CombatMemory> MEMORY = new ConcurrentHashMap<>();
    /** Mobs that recently wounded any player or pet: entity UUID to game time (Righteous Blow). */
    private static final Map<UUID, Long> AGGRESSORS = new ConcurrentHashMap<>();

    public long lastAttackTick = Long.MIN_VALUE / 2;
    public long lastHurtTick = Long.MIN_VALUE / 2;
    public long lastCombatTick = Long.MIN_VALUE / 2;
    /** Last time the player moved more than a hair; for the stand-still Perks. */
    public long lastMoveTick;
    public Vec3 lastPos = Vec3.ZERO;
    public boolean stillnessActive;
    /** Spell Economy's enchanting-table watch: the seed seen when the table was last checked. */
    public boolean watchingSeed;
    public int watchedSeed;

    private final Map<String, Long> windows = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();
    /** Per-target, per-key game times (first hits, hexes). */
    private final Map<String, Map<UUID, Long>> perTarget = new HashMap<>();
    /** Recent hit times for counting "N hits within T" mechanics, per key. */
    private final Map<String, ArrayDeque<Long>> recent = new HashMap<>();

    /** Consecutive-hit tracking (Battle Rhythm, Quivering Strike, Deathmark). */
    public UUID streakTarget;
    public int streakCount;
    public long streakLastTick;
    public final ArrayDeque<double[]> deathmarkLog = new ArrayDeque<>();

    private CombatMemory() {}

    public static CombatMemory of(Player player) {
        return MEMORY.computeIfAbsent(player.getUUID(), id -> new CombatMemory());
    }

    public static void clear(UUID player) {
        MEMORY.remove(player);
    }

    public static void clearAll() {
        MEMORY.clear();
        AGGRESSORS.clear();
    }

    public static int size() {
        return MEMORY.size();
    }

    // -- windows ---------------------------------------------------------------------------------

    public void open(String key, long until) {
        windows.put(key, until);
    }

    public boolean isOpen(String key, long now) {
        Long until = windows.get(key);
        return until != null && until > now;
    }

    /** Closes the window and reports whether it was open. */
    public boolean consume(String key, long now) {
        Long until = windows.remove(key);
        return until != null && until > now;
    }

    public void close(String key) {
        windows.remove(key);
    }

    // -- counters --------------------------------------------------------------------------------

    public int increment(String key) {
        return counters.merge(key, 1, Integer::sum);
    }

    public void reset(String key) {
        counters.remove(key);
    }

    // -- per target ------------------------------------------------------------------------------

    public long lastOn(String key, Entity target) {
        Map<UUID, Long> map = perTarget.get(key);
        Long at = map == null ? null : map.get(target.getUUID());
        return at == null ? Long.MIN_VALUE / 2 : at;
    }

    public void markOn(String key, Entity target, long now) {
        Map<UUID, Long> map = perTarget.computeIfAbsent(key, k -> new HashMap<>());
        map.put(target.getUUID(), now);
        if (map.size() > 256) map.values().removeIf(t -> now - t > 20 * 60);
    }

    // -- "N within T" ----------------------------------------------------------------------------

    /** Records an event now and returns how many happened within the last {@code windowTicks}. */
    public int recordWithin(String key, long now, long windowTicks) {
        ArrayDeque<Long> log = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        log.addLast(now);
        while (!log.isEmpty() && now - log.peekFirst() > windowTicks) log.pollFirst();
        while (log.size() > 64) log.pollFirst();
        return log.size();
    }

    public void clearWithin(String key) {
        recent.remove(key);
    }

    // -- aggressors ------------------------------------------------------------------------------

    public static void markAggressor(Entity attacker, long now) {
        AGGRESSORS.put(attacker.getUUID(), now);
        if (AGGRESSORS.size() > 4096) AGGRESSORS.values().removeIf(t -> now - t > 200);
    }

    public static boolean woundedRecently(Entity attacker, long now, long windowTicks) {
        Long at = AGGRESSORS.get(attacker.getUUID());
        return at != null && now - at <= windowTicks;
    }

    /** Clears the per-death state of a player (windows and streaks end with the body). */
    public static void onDeath(UUID player) {
        MEMORY.remove(player);
    }
}
