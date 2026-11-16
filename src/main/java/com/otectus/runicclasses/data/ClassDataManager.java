package com.otectus.runicclasses.data;

import com.google.gson.JsonParser;
import com.otectus.runicclasses.RunicClasses;
import com.otectus.runicclasses.registry.RCPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The live class definitions, one snapshot per logical side.
 *
 * <p>The server snapshot comes from the datapack ({@link ClassDefinitionLoader}); the client snapshot
 * from {@code S2CClassDefinitionsSync}, which carries the same JSON the server committed, so a client
 * on a dedicated server shows the server's perk levels and numbers rather than its own jar's. In a
 * singleplayer JVM both exist and agree. Each commit adopts the numbers into the registered Perks.
 */
public final class ClassDataManager {

    /** Immutable view of one committed set of definitions. */
    public record Snapshot(Map<ResourceLocation, ClassDefinition> byId,
                           Map<ResourceLocation, ClassDefinition> byOrigin,
                           Map<ResourceLocation, String> rawJson,
                           int revision) {

        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), 0);

        @Nullable
        public ClassDefinition byOrigin(@Nullable ResourceLocation origin) {
            return origin == null ? null : byOrigin.get(origin);
        }

        @Nullable
        public ClassDefinition byId(@Nullable ResourceLocation id) {
            return id == null ? null : byId.get(id);
        }

        @Nullable
        public ClassDefinition byPath(String path) {
            return byId.get(ClassIds.id(path));
        }
    }

    private static volatile Snapshot server = Snapshot.EMPTY;
    private static volatile Snapshot client = Snapshot.EMPTY;
    private static volatile List<ClassDefinitionValidator.Problem> lastProblems = List.of();
    /** Set when a server commit happened and online players have not been reconciled against it yet. */
    private static volatile boolean pendingServerReconcile = false;

    private ClassDataManager() {}

    public static Snapshot server() {
        return server;
    }

    public static Snapshot client() {
        return client;
    }

    public static Snapshot forSide(boolean clientSide) {
        return clientSide ? client : server;
    }

    public static Snapshot forLevel(@Nullable Level level) {
        return level != null && level.isClientSide() ? client : server;
    }

    public static List<ClassDefinitionValidator.Problem> lastProblems() {
        return lastProblems;
    }

    public static boolean takePendingReconcile() {
        boolean pending = pendingServerReconcile;
        pendingServerReconcile = false;
        return pending;
    }

    static Snapshot build(Map<ResourceLocation, ClassDefinition> definitions, Map<ResourceLocation, String> raw, int revision) {
        Map<ResourceLocation, ClassDefinition> byId = new LinkedHashMap<>(definitions);
        Map<ResourceLocation, ClassDefinition> byOrigin = new LinkedHashMap<>();
        for (ClassDefinition def : definitions.values()) byOrigin.put(def.origin(), def);
        return new Snapshot(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byOrigin),
                Collections.unmodifiableMap(new LinkedHashMap<>(raw)), revision);
    }

    /** Server side: the loader validated this set; make it live and adopt its numbers. */
    static void commitServer(Map<ResourceLocation, ClassDefinition> definitions, Map<ResourceLocation, String> raw,
                             List<ClassDefinitionValidator.Problem> problems) {
        Snapshot next = build(definitions, raw, server.revision() + 1);
        server = next;
        lastProblems = List.copyOf(problems);
        adopt(next);
        pendingServerReconcile = true;
    }

    static void rejectServer(List<ClassDefinitionValidator.Problem> problems) {
        lastProblems = List.copyOf(problems);
    }

    /**
     * Client side: parse the JSON the server sent and make it live. A file that does not parse here
     * parsed on the server, so it would mean a version mismatch; it is skipped and logged.
     */
    public static void commitClient(Map<ResourceLocation, String> raw, int revision) {
        Map<ResourceLocation, ClassDefinition> parsed = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> entry : raw.entrySet()) {
            try {
                parsed.put(entry.getKey(), ClassDefinitionParser.parse(entry.getKey(), JsonParser.parseString(entry.getValue())));
            } catch (RuntimeException e) {
                failures.add(entry.getKey() + ": " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) RunicClasses.LOGGER.warn("Skipped class definitions the server sent: {}", failures);
        Snapshot next = build(parsed, raw, revision);
        client = next;
        adopt(next);
    }

    /** Forget the client snapshot (disconnect), so a later world cannot read the previous server's data. */
    public static void clearClient() {
        client = Snapshot.EMPTY;
    }

    private static void adopt(Snapshot snapshot) {
        for (ClassDefinition def : snapshot.byId().values()) {
            try {
                RCPerks.applyTunables(def);
            } catch (RuntimeException e) {
                RunicClasses.LOGGER.warn("Could not adopt perk numbers for {}: {}", def.id(), e.toString());
            }
        }
    }
}
