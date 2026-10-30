package com.hivemc.chunker.conversion.encoding.java.backport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Settings for the modded-world backport (newer Java world to an older modded Java server).
 * Loaded once from the --backport JSON, read from anywhere in the conversion.
 */
public final class BackportSettings {
    private static volatile boolean enabled = false;
    // Entity ids kept verbatim (new vanilla mobs provided by a backport mod, per-wood boats, ...)
    private static Set<String> passthroughEntities = Set.of();
    // Data component types known to the target version (vanilla namespace only)
    private static Set<String> targetComponents = Set.of();
    // Universal Graves data, by grave id
    private static final Map<Long, CompoundTag> graves = new ConcurrentHashMap<>();
    // Counters for the conversion report
    private static final Map<String, AtomicLong> report = new ConcurrentHashMap<>();

    private BackportSettings() {
    }

    static {
        // Enabled by pointing -Dchunker.backport at the settings JSON
        String path = System.getProperty("chunker.backport");
        if (path != null && !path.isEmpty()) {
            try {
                load(new File(path));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read backport settings " + path, e);
            }
        }
    }

    public static void load(File file) throws IOException {
        JsonObject root;
        try (FileReader reader = new FileReader(file)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        passthroughEntities = toSet(root.getAsJsonArray("passthrough_entities"));
        // Defaults to the vanilla 1.21.1 components, "extra_target_components" adds ones a mod (Vanilla Backport) registers
        Set<String> components = root.has("target_components") ? toSet(root.getAsJsonArray("target_components")) : new HashSet<>(ItemDowngrade.TARGET_COMPONENTS);
        components = new HashSet<>(components);
        components.addAll(toSet(root.getAsJsonArray("extra_target_components")));
        targetComponents = Collections.unmodifiableSet(components);
        enabled = true;
    }

    private static Set<String> toSet(JsonArray array) {
        Set<String> set = new HashSet<>();
        if (array != null) {
            for (JsonElement element : array) set.add(element.getAsString());
        }
        return Collections.unmodifiableSet(set);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isPassthroughEntity(String id) {
        return passthroughEntities.contains(id);
    }

    public static boolean isTargetComponent(String id) {
        return targetComponents.contains(id);
    }

    /**
     * Index the Universal Graves saved data (data/universal-graves.dat) so graves can become chests.
     */
    public static void loadGraves(File levelDirectory) {
        File file = new File(levelDirectory, "data/universal-graves.dat");
        if (!enabled || !file.isFile()) return;
        try {
            CompoundTag root = Tag.readGZipJavaNBT(file);
            CompoundTag data = root.getCompound("data") != null ? root.getCompound("data") : root;
            ListTag<CompoundTag, Map<String, Tag<?>>> list = data == null ? null : data.getList("Graves", CompoundTag.class, null);
            if (list == null) return;
            for (CompoundTag grave : list) {
                graves.put(grave.getLong("Id", -1L), grave);
            }
            count("graves indexed", list.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    public static Optional<CompoundTag> getGrave(long id) {
        return Optional.ofNullable(graves.get(id));
    }

    public static void count(String key) {
        count(key, 1);
    }

    public static void count(String key, long amount) {
        report.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(amount);
    }

    public static Map<String, Long> getReport() {
        Map<String, Long> out = new TreeMap<>();
        report.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
