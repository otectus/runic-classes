package com.otectus.runicclasses.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON to {@link ClassDefinition}. Shape errors throw {@link JsonParseException} with a message that
 * names the field; rule errors (wrong counts, unknown ids) are the validator's business.
 */
public final class ClassDefinitionParser {

    private ClassDefinitionParser() {}

    public static ClassDefinition parse(ResourceLocation fileId, JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new JsonParseException("root must be a JSON object");
        JsonObject root = element.getAsJsonObject();
        ResourceLocation id = location(root, "id");
        if (!id.equals(fileId)) {
            throw new JsonParseException("id " + id + " does not match the file name " + fileId);
        }
        ResourceLocation origin = location(root, "origin");
        ResourceLocation icon = null;
        if (root.has("icon")) icon = location(GsonHelper.getAsJsonObject(root, "icon"), "item");
        int color = root.has("color") ? color(GsonHelper.getAsString(root, "color")) : 0xBFA46A;

        ResourceLocation primary = null;
        ResourceLocation secondary = null;
        if (root.has("stats")) {
            JsonObject stats = GsonHelper.getAsJsonObject(root, "stats");
            primary = location(stats, "primary");
            secondary = location(stats, "secondary");
        }
        int startLevel = GsonHelper.getAsInt(root, "start_level", ClassKit.START_LEVEL);
        int capBonus = GsonHelper.getAsInt(root, "cap_bonus", ClassKit.CAP_BONUS);

        List<ClassDefinition.PerkBinding> perks = new ArrayList<>();
        if (root.has("perks")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(root, "perks")) {
                if (!e.isJsonObject()) throw new JsonParseException("every perk entry must be an object");
                JsonObject perk = e.getAsJsonObject();
                ResourceLocation perkId = location(perk, "id");
                String gateName = GsonHelper.getAsString(perk, "gate");
                ClassDefinition.Gate gate;
                try {
                    gate = ClassDefinition.Gate.valueOf(gateName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new JsonParseException("perk " + perkId + ": gate must be primary or secondary, got " + gateName);
                }
                int level = GsonHelper.getAsInt(perk, "level");
                boolean starting = GsonHelper.getAsBoolean(perk, "starting", false);
                Map<String, Double> values = new LinkedHashMap<>();
                if (perk.has("values")) {
                    for (Map.Entry<String, JsonElement> v : GsonHelper.getAsJsonObject(perk, "values").entrySet()) {
                        double number = GsonHelper.convertToDouble(v.getValue(), v.getKey());
                        if (!Double.isFinite(number)) throw new JsonParseException("perk " + perkId + ": value " + v.getKey() + " is not finite");
                        values.put(v.getKey(), number);
                    }
                }
                perks.add(new ClassDefinition.PerkBinding(perkId, gate, level, starting, Collections.unmodifiableMap(values)));
            }
        }

        ClassDefinition.PowerSet powers = null;
        if (root.has("powers")) {
            JsonObject p = GsonHelper.getAsJsonObject(root, "powers");
            powers = new ClassDefinition.PowerSet(location(p, "mark"), location(p, "seal"), location(p, "crown"));
        }

        ClassDefinition.StarterBag bag = null;
        if (root.has("starter_bag")) {
            JsonObject b = GsonHelper.getAsJsonObject(root, "starter_bag");
            bag = new ClassDefinition.StarterBag(location(b, "loot_table"),
                    b.has("bag_type") ? location(b, "bag_type") : null);
        }
        return new ClassDefinition(id, origin, icon, color, primary, secondary, startLevel, capBonus,
                List.copyOf(perks), powers, bag);
    }

    private static ResourceLocation location(JsonObject object, String key) {
        String raw = GsonHelper.getAsString(object, key);
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) throw new JsonParseException(key + ": not a resource location: " + raw);
        return parsed;
    }

    private static int color(@Nullable String raw) {
        if (raw == null || !raw.matches("#[0-9A-Fa-f]{6}")) throw new JsonParseException("color must be #RRGGBB, got " + raw);
        return Integer.parseInt(raw.substring(1), 16);
    }
}
