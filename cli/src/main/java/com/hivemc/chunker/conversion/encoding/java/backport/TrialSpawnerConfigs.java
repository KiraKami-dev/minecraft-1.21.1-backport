package com.hivemc.chunker.conversion.encoding.java.backport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.FloatTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trial spawner configs. From 1.21.2 normal_config / ominous_config are registry names, 1.21.1 wants the config inline.
 * The vanilla configs (data/minecraft/trial_spawner of 1.21.11) are bundled in resources/backport/trial-spawner-configs.json.
 */
public final class TrialSpawnerConfigs {
    private static volatile JsonObject configs;

    private TrialSpawnerConfigs() {
    }

    private static JsonObject configs() {
        if (configs == null) {
            synchronized (TrialSpawnerConfigs.class) {
                if (configs == null) {
                    try (InputStream in = TrialSpawnerConfigs.class.getResourceAsStream("/backport/trial-spawner-configs.json")) {
                        configs = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read trial-spawner-configs.json", e);
                    }
                }
            }
        }
        return configs;
    }

    /**
     * Replace the config names of a trial spawner with the inline configs.
     */
    public static void inline(CompoundTag tag) {
        for (String key : new String[]{"normal_config", "ominous_config"}) {
            if (!(tag.get(key) instanceof StringTag name)) continue;
            String id = name.getValue().contains(":") ? name.getValue() : "minecraft:" + name.getValue();
            JsonElement config = configs().get(id);
            if (config == null) {
                BackportSettings.count("trial spawner config unknown: " + id);
                continue;
            }
            tag.put(key, (CompoundTag) toTag(config));
            BackportSettings.count("trial spawner configs inlined");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tag<?> toTag(JsonElement element) {
        if (element instanceof JsonObject object) {
            CompoundTag compound = new CompoundTag();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                compound.put(entry.getKey(), toTag(entry.getValue()));
            }
            return compound;
        } else if (element instanceof JsonArray array) {
            List<Tag> list = new ArrayList<>();
            for (JsonElement child : array) list.add(toTag(child));
            return new ListTag(list);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return new ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));
        if (primitive.isString()) return new StringTag(primitive.getAsString());
        String text = primitive.getAsString();
        if (text.contains(".") || text.contains("e") || text.contains("E")) return new FloatTag(primitive.getAsFloat());
        return new IntTag(primitive.getAsInt());
    }
}
