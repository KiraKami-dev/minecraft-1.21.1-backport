package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolvers;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;
import com.hivemc.chunker.util.JsonTextUtil;

import java.util.Map;
import java.util.Set;

/**
 * Writes components Chunker didn't (modded ones, and vanilla ones it has no model for) back onto an item,
 * converting the vanilla formats that changed after 1.21.4 back to their 1.21.1 shape.
 */
public final class ComponentDowngrade {
    // Components Chunker writes but loses data on (unknown/modded enchantments), always rebuilt from the original
    private static final Set<String> OVERRIDE = Set.of("minecraft:enchantments", "minecraft:stored_enchantments");
    // Text components stored as NBT from 1.21.5, JSON strings before
    private static final Set<String> TEXT = Set.of("minecraft:item_name", "minecraft:custom_name");

    private ComponentDowngrade() {
    }

    public static void apply(JavaResolvers writer, ExtraComponents extra, CompoundTag output) {
        boolean newFormat = extra.source().getVersion().isGreaterThanOrEqual(1, 21, 5);
        boolean oldTarget = writer.dataVersion().getVersion().isLessThan(1, 21, 5);
        CompoundTag components = null;
        for (Map.Entry<String, Tag<?>> entry : extra.components()) {
            String key = entry.getKey();
            String baseKey = key.startsWith("!") ? key.substring(1) : key;
            CompoundTag existing = output.getCompound("components");
            boolean present = existing != null && existing.contains(key);
            if (present && !OVERRIDE.contains(key)) continue;

            Tag<?> value = EmbeddedNBT.rewrite(writer, entry.getValue(), extra.embedded());
            if (newFormat && oldTarget && baseKey.startsWith("minecraft:")) {
                if (!BackportSettings.isTargetComponent(baseKey)) {
                    convertRemoved(key, value, output);
                    continue;
                }
                if (!key.startsWith("!")) value = downgrade(key, value);
                if (value == null) {
                    BackportSettings.count("component dropped: " + key);
                    continue;
                }
            }
            if (components == null) components = output.getOrCreateCompound("components");
            components.put(key, value);
        }
    }

    // Components that no longer exist on the target: carry what we can over to the old ones
    private static void convertRemoved(String key, Tag<?> value, CompoundTag output) {
        if (key.equals("minecraft:tooltip_display") && value instanceof CompoundTag tooltip) {
            if (tooltip.getByte("hide_tooltip", (byte) 0) == 1) {
                output.getOrCreateCompound("components").put("minecraft:hide_tooltip", new CompoundTag());
            }
            return;
        }
        BackportSettings.count("component dropped (not in target): " + key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tag<?> downgrade(String key, Tag<?> value) {
        if (TEXT.contains(key)) {
            return new StringTag(JsonTextUtil.toJSON(value instanceof StringTag string
                    ? JsonTextUtil.fromText(string.getValue())
                    : JsonTextUtil.fromNBT(value)));
        }
        switch (key) {
            case "minecraft:enchantments", "minecraft:stored_enchantments" -> {
                // 1.21.5 dropped the {levels: {...}, show_in_tooltip} wrapper
                if (value instanceof CompoundTag map && !map.contains("levels")) {
                    CompoundTag wrapped = new CompoundTag(1);
                    wrapped.put("levels", map);
                    return wrapped;
                }
                return value;
            }
            case "minecraft:attribute_modifiers" -> {
                if (value instanceof ListTag list) {
                    CompoundTag wrapped = new CompoundTag(1);
                    wrapped.put("modifiers", list);
                    return wrapped;
                }
                return value;
            }
            case "minecraft:custom_model_data" -> {
                if (value instanceof CompoundTag data) {
                    if (data.get("floats") instanceof ListTag floats && floats.size() > 0
                            && ((Tag<?>) floats.get(0)).getBoxedValue() instanceof Number number) {
                        return new IntTag(number.intValue());
                    }
                    return null;
                }
                return value;
            }
            case "minecraft:unbreakable", "minecraft:hide_additional_tooltip", "minecraft:hide_tooltip",
                 "minecraft:creative_slot_lock", "minecraft:intangible_projectile" -> {
                return value instanceof CompoundTag ? value : new CompoundTag();
            }
            case "minecraft:lock" -> {
                return null; // was a plain string, now an item predicate
            }
            default -> {
                return value;
            }
        }
    }
}
