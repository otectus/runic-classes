package com.otectus.runicclasses.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.otectus.runicclasses.RunicClasses;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/runicclasses/classes/*.json}, validates the whole set, and commits it only if it
 * is free of errors. On an error the previous snapshot stays live and every problem is logged, so a
 * broken datapack edit cannot strip class members of their grants (fail-closed, the Runic Gods data
 * manager's pattern). Files under other namespaces in a {@code classes} folder belong to other mods
 * and are ignored.
 */
public final class ClassDefinitionLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    public static final String FOLDER = "classes";

    public ClassDefinitionLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ClassDefinition> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, String> raw = new LinkedHashMap<>();
        List<ClassDefinitionValidator.Problem> problems = new ArrayList<>();
        files.entrySet().stream()
                .filter(e -> ClassIds.MOD_ID.equals(e.getKey().getNamespace()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        parsed.put(entry.getKey(), ClassDefinitionParser.parse(entry.getKey(), entry.getValue()));
                        raw.put(entry.getKey(), GSON.toJson(entry.getValue()));
                    } catch (RuntimeException e) {
                        problems.add(new ClassDefinitionValidator.Problem(ClassDefinitionValidator.Severity.ERROR,
                                entry.getKey() + ": " + e.getMessage()));
                    }
                });
        problems.addAll(ClassDefinitionValidator.validate(parsed));
        for (ClassDefinitionValidator.Problem problem : problems) {
            if (problem.severity() == ClassDefinitionValidator.Severity.ERROR) RunicClasses.LOGGER.error("Class data: {}", problem.message());
            else RunicClasses.LOGGER.warn("Class data: {}", problem.message());
        }
        if (ClassDefinitionValidator.hasErrors(problems)) {
            ClassDataManager.rejectServer(problems);
            RunicClasses.LOGGER.error("Class data rejected; the previous {} definition(s) stay live.",
                    ClassDataManager.server().byId().size());
            return;
        }
        ClassDataManager.commitServer(parsed, raw, problems);
        RunicClasses.LOGGER.info("Loaded {} class definition(s).", parsed.size());
    }
}
