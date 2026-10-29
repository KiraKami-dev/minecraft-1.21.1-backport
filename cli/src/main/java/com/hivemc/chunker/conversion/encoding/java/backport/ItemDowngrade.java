package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;
import com.hivemc.chunker.util.JsonTextUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts item stacks written by 1.21.5+ (components as NBT, flat enchantments, no tooltip flags ...) to the
 * 1.21.1 shape. This works on the raw item NBT so ids and components Chunker doesn't know about (modded items,
 * modded components) are kept exactly as they were.
 */
public final class ItemDowngrade {
    // Attribute ids lost their generic./player. prefix in 1.21.2 (the zombie one is the only other prefix)
    private static final Set<String> GENERIC = Set.of("armor", "armor_toughness", "attack_damage", "attack_knockback",
            "attack_speed", "burning_time", "explosion_knockback_resistance", "fall_damage_multiplier", "flying_speed",
            "follow_range", "gravity", "jump_strength", "knockback_resistance", "luck", "max_absorption", "max_health",
            "movement_efficiency", "movement_speed", "oxygen_bonus", "safe_fall_distance", "scale", "step_height",
            "water_movement_efficiency");
    private static final Set<String> PLAYER = Set.of("block_break_speed", "block_interaction_range",
            "entity_interaction_range", "mining_efficiency", "sneaking_speed", "submerged_mining_speed",
            "sweeping_damage_ratio");
    // Components with a show_in_tooltip flag in 1.21.1 (the new tooltip_display hides them by id)
    private static final Set<String> TOOLTIP_FLAG = Set.of("minecraft:attribute_modifiers", "minecraft:enchantments",
            "minecraft:stored_enchantments", "minecraft:unbreakable", "minecraft:dyed_color", "minecraft:trim",
            "minecraft:can_place_on", "minecraft:can_break", "minecraft:jukebox_playable");

    /**
     * The data component types of vanilla 1.21.1.
     */
    public static final Set<String> TARGET_COMPONENTS = Set.of("custom_data", "max_stack_size", "max_damage", "damage",
            "unbreakable", "custom_name", "item_name", "lore", "rarity", "enchantments", "can_place_on", "can_break",
            "attribute_modifiers", "custom_model_data", "hide_additional_tooltip", "hide_tooltip", "repair_cost",
            "creative_slot_lock", "enchantment_glint_override", "intangible_projectile", "food", "fire_resistant", "tool",
            "stored_enchantments", "dyed_color", "map_color", "map_id", "map_decorations", "map_post_processing",
            "charged_projectiles", "bundle_contents", "potion_contents", "suspicious_stew_effects",
            "writable_book_content", "written_book_content", "trim", "debug_stick_state", "entity_data",
            "bucket_entity_data", "block_entity_data", "instrument", "ominous_bottle_amplifier", "jukebox_playable",
            "recipes", "lodestone_tracker", "firework_explosion", "fireworks", "profile", "note_block_sound",
            "banner_patterns", "base_color", "pot_decorations", "container", "block_state", "bees", "lock",
            "container_loot").stream().map(s -> "minecraft:" + s).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private ItemDowngrade() {
    }

    public static boolean applies(JavaDataVersion source, JavaDataVersion target) {
        return source != null && source.getVersion().isGreaterThanOrEqual(1, 21, 5)
                && target.getVersion().isLessThan(1, 21, 2);
    }

    /**
     * Convert an item stack.
     *
     * @return a new compound, or null if the item has no equivalent and should be removed.
     */
    public static CompoundTag item(CompoundTag input, JavaDataVersion source, JavaDataVersion target) {
        CompoundTag item = input.clone();
        if (!ItemSubstitutions.apply(item)) return null;

        CompoundTag components = item.getCompound("components");
        if (components != null && applies(source, target)) {
            item.put("components", components(components, source, target));
        } else if (components != null) {
            walkChildren(components, source, target);
        }
        CompoundTag out = item.getCompound("components");
        if (out != null && out.size() == 0) item.remove("components");
        return item;
    }

    /**
     * Convert every item stack found anywhere inside a tag (in place for compounds / lists).
     *
     * @return the tag to use in place of the input, or null if it was an item that has to be removed.
     */
    public static Tag<?> walk(Tag<?> tag, JavaDataVersion source, JavaDataVersion target) {
        if (tag instanceof CompoundTag compound) {
            if (EmbeddedNBT.isItem(compound)) return item(compound, source, target);
            walkChildren(compound, source, target);
        } else if (tag instanceof ListTag<?, ?> list) {
            walkList(list, source, target);
        }
        return tag;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void walkList(ListTag list, JavaDataVersion source, JavaDataVersion target) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Tag<?> child = (Tag<?>) list.get(i);
            Tag<?> replaced = walk(child, source, target);
            if (replaced == null) list.getValue().remove(i);
            else if (replaced != child) list.set(i, replaced);
        }
    }

    private static void walkChildren(CompoundTag compound, JavaDataVersion source, JavaDataVersion target) {
        for (String key : new java.util.ArrayList<>(compound.getValue().keySet())) {
            Tag<?> child = compound.get(key);
            Tag<?> replaced = walk(child, source, target);
            if (replaced == null) compound.remove(key);
            else if (replaced != child) compound.put(key, replaced);
        }
    }

    private static CompoundTag components(CompoundTag input, JavaDataVersion source, JavaDataVersion target) {
        CompoundTag out = new CompoundTag(input.size());
        Set<String> hidden = new HashSet<>();
        boolean hideAll = false;

        CompoundTag tooltip = input.getCompound("minecraft:tooltip_display");
        if (tooltip != null) {
            hideAll = tooltip.getByte("hide_tooltip", (byte) 0) == 1;
            if (tooltip.get("hidden_components") instanceof ListTag<?, ?> list) {
                for (Object o : list) if (o instanceof StringTag s) hidden.add(s.getValue());
            }
        }

        for (String key : new java.util.ArrayList<>(input.getValue().keySet())) {
            Tag<?> value = input.get(key);
            boolean removal = key.startsWith("!");
            String base = removal ? key.substring(1) : key;

            if (base.equals("minecraft:tooltip_display")) continue;
            if (!base.startsWith("minecraft:")) {
                // Modded component: kept as is, with any item inside converted
                out.put(key, removal ? value.clone() : walkCopy(value, source, target));
                continue;
            }
            if (removal) {
                // Removing a default component only means something if the target has it
                if (BackportSettings.isTargetComponent(base)) out.put(key, value.clone());
                else BackportSettings.count("component removal dropped: " + base);
                continue;
            }
            if (!BackportSettings.isTargetComponent(base)) {
                convertRemoved(base, value, out);
                continue;
            }
            Tag<?> converted = convert(base, value, hidden.contains(base), source, target);
            if (converted == null) {
                BackportSettings.count("component dropped: " + base);
                continue;
            }
            out.put(key, converted);
        }

        if (hideAll) out.put("minecraft:hide_tooltip", new CompoundTag());
        for (String h : hidden) {
            if (!TOOLTIP_FLAG.contains(h) && !h.equals("minecraft:attribute_modifiers") && BackportSettings.isTargetComponent(h)
                    && !out.contains("minecraft:hide_additional_tooltip")) {
                out.put("minecraft:hide_additional_tooltip", new CompoundTag());
            }
        }
        return out;
    }

    private static Tag<?> walkCopy(Tag<?> value, JavaDataVersion source, JavaDataVersion target) {
        Tag<?> copy = value.clone();
        Tag<?> walked = walk(copy, source, target);
        return walked == null ? new CompoundTag() : walked;
    }

    // Components that don't exist on the target: carry over what has an old equivalent
    private static void convertRemoved(String base, Tag<?> value, CompoundTag out) {
        if (base.equals("minecraft:damage_resistant") && value instanceof CompoundTag resistant
                && resistant.getString("types", "").contains("is_fire")) {
            out.put("minecraft:fire_resistant", new CompoundTag());
            return;
        }
        BackportSettings.count("component dropped (not in target): " + base);
    }

    private static Tag<?> convert(String key, Tag<?> value, boolean hidden, JavaDataVersion source, JavaDataVersion target) {
        switch (key) {
            case "minecraft:custom_name", "minecraft:item_name" -> {
                return text(value);
            }
            case "minecraft:lore" -> {
                if (!(value instanceof ListTag<?, ?> list)) return null;
                ListTag<StringTag, String> lines = new ListTag<>(TagType.STRING, list.size());
                for (Object line : list) lines.add(text((Tag<?>) line));
                return lines;
            }
            case "minecraft:enchantments", "minecraft:stored_enchantments" -> {
                CompoundTag levels = value instanceof CompoundTag c ? (c.get("levels") instanceof CompoundTag l ? l : c) : null;
                if (levels == null) return null;
                CompoundTag wrapped = new CompoundTag(2);
                wrapped.put("levels", levels.clone());
                if (hidden) wrapped.put("show_in_tooltip", (byte) 0);
                return wrapped;
            }
            case "minecraft:attribute_modifiers" -> {
                ListTag<?, ?> list = value instanceof ListTag<?, ?> l ? l
                        : value instanceof CompoundTag c && c.get("modifiers") instanceof ListTag<?, ?> m ? m : null;
                if (list == null) return null;
                ListTag<CompoundTag, Map<String, Tag<?>>> modifiers = new ListTag<>(TagType.COMPOUND, list.size());
                for (Object o : list) {
                    if (!(o instanceof CompoundTag modifier)) continue;
                    CompoundTag copy = modifier.clone();
                    copy.remove("display");
                    String type = copy.getString("type", "");
                    String renamed = oldAttribute(type);
                    if (renamed == null) {
                        BackportSettings.count("attribute modifier dropped: " + type);
                        continue;
                    }
                    copy.put("type", renamed);
                    modifiers.add(copy);
                }
                CompoundTag wrapped = new CompoundTag(2);
                wrapped.put("modifiers", modifiers);
                if (hidden) wrapped.put("show_in_tooltip", (byte) 0);
                return wrapped;
            }
            case "minecraft:unbreakable" -> {
                CompoundTag flag = new CompoundTag();
                if (hidden) flag.put("show_in_tooltip", (byte) 0);
                return flag;
            }
            case "minecraft:dyed_color" -> {
                if (!hidden) return value.clone();
                CompoundTag colour = new CompoundTag(2);
                colour.put("rgb", value instanceof IntTag i ? i.getValue() : ((CompoundTag) value).getInt("rgb", 0));
                colour.put("show_in_tooltip", (byte) 0);
                return colour;
            }
            case "minecraft:trim", "minecraft:can_place_on", "minecraft:can_break" -> {
                if (!(value instanceof CompoundTag c)) return null;
                CompoundTag copy = c.clone();
                if (!key.equals("minecraft:trim") && !copy.contains("predicates")) {
                    // Newer versions allow a bare predicate, wrap it
                    CompoundTag wrapped = new CompoundTag(2);
                    ListTag<CompoundTag, Map<String, Tag<?>>> predicates = new ListTag<>(TagType.COMPOUND, 1);
                    predicates.add(copy);
                    wrapped.put("predicates", predicates);
                    copy = wrapped;
                }
                if (hidden) copy.put("show_in_tooltip", (byte) 0);
                return copy;
            }
            case "minecraft:jukebox_playable" -> {
                CompoundTag song = new CompoundTag(2);
                if (value instanceof StringTag s) song.put("song", s.getValue());
                else if (value instanceof CompoundTag c) song = c.clone();
                else return null;
                if (hidden) song.put("show_in_tooltip", (byte) 0);
                return song;
            }
            case "minecraft:custom_model_data" -> {
                if (value instanceof IntTag) return value.clone();
                if (value instanceof CompoundTag data && data.get("floats") instanceof ListTag<?, ?> floats && floats.size() > 0
                        && ((Tag<?>) floats.get(0)).getBoxedValue() instanceof Number number) {
                    return new IntTag(number.intValue());
                }
                return null;
            }
            case "minecraft:lock" -> {
                if (value instanceof StringTag) return value.clone();
                // An item predicate now, the old lock compared the custom name
                if (value instanceof CompoundTag c && c.getCompound("components") != null
                        && c.getCompound("components").get("minecraft:custom_name") != null) {
                    return new StringTag(JsonTextUtil.toStripped(JsonTextUtil.fromNBT(c.getCompound("components").get("minecraft:custom_name"))));
                }
                return null;
            }
            case "minecraft:written_book_content" -> {
                return book(value);
            }
            case "minecraft:hide_tooltip", "minecraft:hide_additional_tooltip", "minecraft:creative_slot_lock",
                 "minecraft:intangible_projectile" -> {
                return value instanceof CompoundTag ? value : new CompoundTag();
            }
            case "minecraft:entity_data", "minecraft:bucket_entity_data", "minecraft:block_entity_data" -> {
                CompoundTag copy = (CompoundTag) value.clone();
                if (!key.equals("minecraft:block_entity_data")) EntityDowngrade.entity(copy, source, target);
                walkChildren(copy, source, target);
                return copy;
            }
            default -> {
                // bundle_contents, container, charged_projectiles ... hold items, everything else is unchanged
                return walkCopy(value, source, target);
            }
        }
    }

    private static StringTag text(Tag<?> value) {
        if (value instanceof StringTag string) {
            return new StringTag(JsonTextUtil.toJSON(JsonTextUtil.fromText(string.getValue())));
        }
        return new StringTag(JsonTextUtil.toJSON(JsonTextUtil.fromNBT(value)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tag<?> book(Tag<?> value) {
        if (!(value instanceof CompoundTag book)) return null;
        CompoundTag copy = book.clone();
        if (copy.get("pages") instanceof ListTag<?, ?> pages) {
            ListTag converted = new ListTag<>(TagType.COMPOUND, pages.size());
            for (Object o : pages) {
                Tag<?> page = (Tag<?>) o;
                if (page instanceof CompoundTag filterable && filterable.contains("raw")) {
                    CompoundTag p = new CompoundTag(2);
                    p.put("raw", text(filterable.get("raw")));
                    if (filterable.contains("filtered")) p.put("filtered", text(filterable.get("filtered")));
                    converted.add(p);
                } else {
                    CompoundTag p = new CompoundTag(1);
                    p.put("raw", text(page));
                    converted.add(p);
                }
            }
            copy.put("pages", converted);
        }
        return copy;
    }

    /**
     * Turn a 1.21.2+ attribute id back into the 1.21.1 one.
     *
     * @return the old id or null if the attribute doesn't exist in 1.21.1.
     */
    public static String oldAttribute(String id) {
        if (!id.startsWith("minecraft:")) return id; // modded
        String name = id.substring("minecraft:".length());
        if (GENERIC.contains(name)) return "minecraft:generic." + name;
        if (PLAYER.contains(name)) return "minecraft:player." + name;
        if (name.equals("spawn_reinforcements")) return "minecraft:zombie.spawn_reinforcements";
        if (name.startsWith("generic.") || name.startsWith("player.") || name.startsWith("zombie.")) return id; // already old
        return null;
    }
}
