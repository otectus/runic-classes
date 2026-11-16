package com.otectus.runicclasses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Equal budget per class (docs/plan.md 4), over the committed class JSON: two distinct stats (never
 * Building), start level 8 and cap bonus 8, six Perks on the fixed 8/8/16/16/24/36 ladder with exactly
 * the first two starting, one Power per tier, a starter bag, and no Perk or Power shared by two classes.
 * Magnitudes are reviewed by hand against the band table; this pins the structure.
 */
class ClassBudgetTest {

    private static final Path CLASSES = Path.of("src/main/resources/data/runicclasses/classes");
    private static final List<String> GATES = List.of("primary", "secondary", "primary", "secondary", "primary", "secondary");
    private static final List<Integer> LEVELS = List.of(8, 8, 16, 16, 24, 36);

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("cannot read " + file, e);
        }
    }

    private static List<Path> classFiles() throws IOException {
        try (Stream<Path> files = Files.list(CLASSES)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void thirteenClassesAndClassless() throws IOException {
        List<Path> files = classFiles();
        assertEquals(14, files.size(), "13 classes plus classless.json");
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("classless.json")));
    }

    @Test
    void everyClassHasTheSameBudget() throws IOException {
        Set<String> perks = new HashSet<>();
        Set<String> powers = new HashSet<>();
        for (Path file : classFiles()) {
            JsonObject def = read(file);
            String name = file.getFileName().toString();
            assertTrue(def.has("starter_bag"), name + ": starter bag");
            if (name.equals("classless.json")) {
                assertFalse(def.has("stats") || def.has("perks") || def.has("powers"), "Classless grants nothing");
                continue;
            }
            JsonObject stats = def.getAsJsonObject("stats");
            String primary = stats.get("primary").getAsString();
            String secondary = stats.get("secondary").getAsString();
            assertNotEquals(primary, secondary, name + ": two distinct stats");
            assertFalse(primary.endsWith(":building") || secondary.endsWith(":building"), name + ": Building stays unassigned (OQ-11)");
            assertEquals(8, def.get("start_level").getAsInt(), name + ": start level");
            assertEquals(8, def.get("cap_bonus").getAsInt(), name + ": cap bonus");

            JsonArray perkList = def.getAsJsonArray("perks");
            assertEquals(6, perkList.size(), name + ": six perks");
            int starting = 0;
            for (int i = 0; i < 6; i++) {
                JsonObject perk = perkList.get(i).getAsJsonObject();
                assertEquals(GATES.get(i), perk.get("gate").getAsString(), name + ": perk " + i + " gate");
                assertEquals(LEVELS.get(i), perk.get("level").getAsInt(), name + ": perk " + i + " level");
                boolean isStarting = perk.has("starting") && perk.get("starting").getAsBoolean();
                assertEquals(i < 2, isStarting, name + ": only the first two perks start enabled");
                if (isStarting) starting++;
                assertTrue(perks.add(perk.get("id").getAsString()), "perk listed by two classes: " + perk.get("id"));
                assertTrue(perk.get("id").getAsString().startsWith("runicclasses:cls_"), "class perk ids carry the cls_ prefix");
            }
            assertEquals(2, starting, name + ": two starting perks");

            JsonObject powerSet = def.getAsJsonObject("powers");
            assertEquals(3, powerSet.size(), name + ": one power per tier");
            for (String tier : List.of("mark", "seal", "crown")) {
                JsonElement id = powerSet.get(tier);
                assertTrue(id != null && powers.add(id.getAsString()), name + ": " + tier + " present and unique");
            }
        }
        assertEquals(78, perks.size());
        assertEquals(39, powers.size());
    }
}
