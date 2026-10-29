package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.DoubleTag;
import com.hivemc.chunker.nbt.tags.primitive.FloatTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;
import com.hivemc.chunker.util.JsonTextUtil;

import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolvers;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the entity / player NBT fields that changed between 1.21.1 and 1.21.11 back to their 1.21.1 form.
 * Only the fields the old game would otherwise lose data on are converted, unknown new keys are ignored by it.
 */
public final class EntityDowngrade {
    private static final Pattern BOAT = Pattern.compile("(minecraft|biomesoplenty):([a-z_]+?)_(chest_)?(boat|raft)");
    private static final Set<String> LLAMAS = Set.of("minecraft:llama", "minecraft:trader_llama");
    private static final Set<String> WOLF_VARIANTS = Set.of("ashen", "black", "chestnut", "pale", "rusty", "snowy", "spotted", "striped", "woods");
    private static final Set<String> FARM_VARIANTS = Set.of("temperate", "cold", "warm"); // cow / pig / chicken (Vanilla Backport)
    // 1.21.1 attribute ids by their 1.21.2+ short name
    private static final Map<String, String> ATTRIBUTES = Map.ofEntries(
            Map.entry("armor", "generic.armor"), Map.entry("armor_toughness", "generic.armor_toughness"),
            Map.entry("attack_damage", "generic.attack_damage"), Map.entry("attack_knockback", "generic.attack_knockback"),
            Map.entry("attack_speed", "generic.attack_speed"), Map.entry("burning_time", "generic.burning_time"),
            Map.entry("explosion_knockback_resistance", "generic.explosion_knockback_resistance"),
            Map.entry("fall_damage_multiplier", "generic.fall_damage_multiplier"), Map.entry("flying_speed", "generic.flying_speed"),
            Map.entry("follow_range", "generic.follow_range"), Map.entry("gravity", "generic.gravity"),
            Map.entry("jump_strength", "generic.jump_strength"), Map.entry("knockback_resistance", "generic.knockback_resistance"),
            Map.entry("luck", "generic.luck"), Map.entry("max_absorption", "generic.max_absorption"),
            Map.entry("max_health", "generic.max_health"), Map.entry("movement_efficiency", "generic.movement_efficiency"),
            Map.entry("movement_speed", "generic.movement_speed"), Map.entry("oxygen_bonus", "generic.oxygen_bonus"),
            Map.entry("safe_fall_distance", "generic.safe_fall_distance"), Map.entry("scale", "generic.scale"),
            Map.entry("step_height", "generic.step_height"), Map.entry("water_movement_efficiency", "generic.water_movement_efficiency"),
            Map.entry("block_break_speed", "player.block_break_speed"), Map.entry("block_interaction_range", "player.block_interaction_range"),
            Map.entry("entity_interaction_range", "player.entity_interaction_range"), Map.entry("mining_efficiency", "player.mining_efficiency"),
            Map.entry("sneaking_speed", "player.sneaking_speed"), Map.entry("submerged_mining_speed", "player.submerged_mining_speed"),
            Map.entry("sweeping_damage_ratio", "player.sweeping_damage_ratio"), Map.entry("spawn_reinforcements", "zombie.spawn_reinforcements")
    );
    // Mobs provided by a backport mod keep their 1.21.5+ style home / still fields
    private static final Set<String> BACKPORTED = Set.of("minecraft:happy_ghast", "minecraft:nautilus", "minecraft:zombie_nautilus", "minecraft:copper_golem");
    private static final Set<String> HORSES = Set.of("minecraft:horse", "minecraft:donkey", "minecraft:mule",
            "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:camel", "minecraft:llama", "minecraft:trader_llama");
    private static final Set<String> SADDLE_FLAG = Set.of("minecraft:pig", "minecraft:strider");
    private static final String[] ARMOR = {"feet", "legs", "chest", "head"};
    private static final byte[] ARMOR_SLOTS = {100, 101, 102, 103};

    private EntityDowngrade() {
    }

    public static boolean applies(JavaDataVersion source, JavaDataVersion target) {
        return source != null && source.getVersion().isGreaterThanOrEqual(1, 21, 5)
                && target.getVersion().isLessThan(1, 21, 5);
    }

    /**
     * Downgrade an entity compound in place (including passengers).
     */
    public static void entity(CompoundTag tag, JavaDataVersion source, JavaDataVersion target) {
        String id = tag.getString("id", "");

        // Per-wood boats were one entity with a Type before 1.21.2
        if (target.getVersion().isLessThan(1, 21, 2)) {
            Matcher matcher = BOAT.matcher(id);
            if (matcher.matches()) {
                tag.put("id", matcher.group(1) + (matcher.group(3) != null ? ":chest_boat" : ":boat"));
                tag.put("Type", matcher.group(2));
                BackportSettings.count("boat downgraded: " + id);
            }
        }

        ModCompat.clean(tag);
        if (applies(source, target)) {
            common(tag);
            equipment(tag, id);
            dropChances(tag);
            mobFields(tag, id);
            spawner(tag, source, target);
        }

        ListTag<CompoundTag, Map<String, Tag<?>>> passengers = tag.getList("Passengers", CompoundTag.class, null);
        if (passengers != null) {
            for (CompoundTag passenger : passengers) entity(passenger, source, target);
        }
    }

    /**
     * Downgrade a player data file in place: equipment goes back into the inventory slots.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void player(CompoundTag tag, JavaDataVersion source, JavaDataVersion target) {
        if (!applies(source, target)) return;
        common(tag);
        CompoundTag equipment = tag.getCompound("equipment");
        if (equipment != null) {
            ListTag inventory = tag.getList("Inventory", CompoundTag.class, null);
            if (inventory == null) {
                inventory = new ListTag<>(TagType.COMPOUND);
                tag.put("Inventory", inventory);
            }
            for (int i = 0; i < ARMOR.length; i++) {
                addSlot(inventory, equipment.getCompound(ARMOR[i]), ARMOR_SLOTS[i]);
            }
            addSlot(inventory, equipment.getCompound("offhand"), (byte) -106);
            tag.remove("equipment");
        }
        BackportSettings.count("players converted");
        // Shoulder parrots are entities
        for (String shoulder : new String[]{"ShoulderEntityLeft", "ShoulderEntityRight"}) {
            CompoundTag entity = tag.getCompound(shoulder);
            if (entity != null && entity.size() > 0) entity(entity, source, target);
        }
        CompoundTag vehicle = tag.getCompound("RootVehicle");
        if (vehicle != null && vehicle.getCompound("Entity") != null) entity(vehicle.getCompound("Entity"), source, target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addSlot(ListTag inventory, CompoundTag item, byte slot) {
        if (item == null || item.size() == 0) return;
        item.put("Slot", new ByteTag(slot));
        inventory.add(item);
    }

    private static void common(CompoundTag tag) {
        // Text components went from JSON strings to NBT in 1.21.5
        jsonText(tag, "CustomName");
        if (tag.get("fall_distance") instanceof DoubleTag fall) {
            tag.put("FallDistance", (float) (double) fall.getValue());
            tag.remove("fall_distance");
        }
        attributes(tag);
        leash(tag);
        int[] sleeping = tag.getIntArray("sleeping_pos", null);
        if (sleeping != null && sleeping.length == 3) {
            tag.put("SleepingX", sleeping[0]);
            tag.put("SleepingY", sleeping[1]);
            tag.put("SleepingZ", sleeping[2]);
        }
    }

    public static void textToJson(CompoundTag tag, String key) {
        Tag<?> text = tag.get(key);
        if (text == null) return;
        if (text instanceof StringTag string) {
            // A plain string was literal text in the new format
            tag.put(key, JsonTextUtil.toJSON(JsonTextUtil.fromText(string.getValue())));
        } else {
            tag.put(key, JsonTextUtil.toJSON(JsonTextUtil.fromNBT(text)));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void equipment(CompoundTag tag, String id) {
        CompoundTag equipment = tag.getCompound("equipment");
        if (equipment == null) return;

        ListTag hands = new ListTag<>(TagType.COMPOUND, 2);
        hands.add(orEmpty(equipment.getCompound("mainhand")));
        hands.add(orEmpty(equipment.getCompound("offhand")));
        tag.put("HandItems", hands);

        ListTag armor = new ListTag<>(TagType.COMPOUND, 4);
        for (String slot : ARMOR) armor.add(orEmpty(equipment.getCompound(slot)));
        tag.put("ArmorItems", armor);

        CompoundTag body = equipment.getCompound("body");
        if (body != null) tag.put("body_armor_item", body);

        CompoundTag saddle = equipment.getCompound("saddle");
        if (saddle != null) {
            if (HORSES.contains(id)) {
                tag.put("SaddleItem", saddle);
            } else if (SADDLE_FLAG.contains(id)) {
                tag.put("Saddle", (byte) 1);
            }
        }
        tag.remove("equipment");
    }

    private static CompoundTag orEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dropChances(CompoundTag tag) {
        CompoundTag chances = tag.getCompound("drop_chances");
        if (chances == null) return;
        float base = 0.085F;
        ListTag hands = new ListTag<>(TagType.FLOAT, 2);
        hands.add(new FloatTag(chances.getFloat("mainhand", base)));
        hands.add(new FloatTag(chances.getFloat("offhand", base)));
        tag.put("HandDropChances", hands);
        ListTag armor = new ListTag<>(TagType.FLOAT, 4);
        for (String slot : ARMOR) armor.add(new FloatTag(chances.getFloat(slot, base)));
        tag.put("ArmorDropChances", armor);
        if (chances.contains("body")) tag.put("body_armor_drop_chance", chances.getFloat("body", base));
        tag.remove("drop_chances");
    }

    /**
     * Convert an attribute id from 1.21.2+ ("minecraft:max_health") to the 1.21.1 one ("minecraft:generic.max_health").
     *
     * @return the old id, the same id for non-vanilla attributes, or null if the attribute does not exist on 1.21.1.
     */
    public static String attributeToOld(String id) {
        if (!id.startsWith("minecraft:")) return id;
        String name = id.substring("minecraft:".length());
        if (name.contains(".")) return id; // already old
        String old = ATTRIBUTES.get(name);
        return old == null ? null : "minecraft:" + old;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void attributes(CompoundTag tag) {
        ListTag<CompoundTag, Map<String, Tag<?>>> list = tag.getList("attributes", CompoundTag.class, null);
        if (list == null) return;
        for (Iterator<CompoundTag> it = list.iterator(); it.hasNext(); ) {
            CompoundTag attribute = it.next();
            String id = attribute.getString("id", "");
            String old = attributeToOld(id);
            if (old == null) {
                BackportSettings.count("attribute dropped: " + id);
                it.remove();
            } else if (!old.equals(id)) {
                attribute.put("id", old);
            }
        }
    }

    private static void leash(CompoundTag tag) {
        // 1.21.5: leash is an int array (4 = holder UUID, 3 = knot position), before: Leash {UUID} / {X, Y, Z}
        int[] leash = tag.get("leash") instanceof com.hivemc.chunker.nbt.tags.array.IntArrayTag array ? array.getValue() : null;
        if (leash == null) return;
        CompoundTag old = new CompoundTag(3);
        if (leash.length == 4) {
            old.put("UUID", leash);
        } else if (leash.length == 3) {
            old.put("X", leash[0]);
            old.put("Y", leash[1]);
            old.put("Z", leash[2]);
        } else {
            return;
        }
        tag.remove("leash");
        tag.put("Leash", old);
    }

    private static void mobFields(CompoundTag tag, String id) {
        // Fields the old game never read
        tag.remove("last_hurt_by_mob");
        tag.remove("last_hurt_by_player");
        tag.remove("ticks_since_last_hurt_by_mob");
        tag.remove("ticks_since_last_hurt_by_player");
        tag.remove("sound_variant");
        if (tag.contains("anger_end_time")) {
            // Was an absolute game time, the old field counts down; the mob simply calms down
            tag.remove("anger_end_time");
            if (!tag.contains("AngerTime")) tag.put("AngerTime", 0);
        }
        if (!BACKPORTED.contains(id)) {
            tag.remove("home_radius");
            tag.remove("still_timeout");
            int[] home = tag.get("home_pos") instanceof com.hivemc.chunker.nbt.tags.array.IntArrayTag array ? array.getValue() : null;
            if (home != null && home.length == 3 && id.equals("minecraft:turtle")) {
                tag.put("HomePosX", home[0]);
                tag.put("HomePosY", home[1]);
                tag.put("HomePosZ", home[2]);
            }
            tag.remove("home_pos");
            int[] travel = tag.get("travel_pos") instanceof com.hivemc.chunker.nbt.tags.array.IntArrayTag array ? array.getValue() : null;
            if (travel != null && travel.length == 3) {
                tag.put("TravelPosX", travel[0]);
                tag.put("TravelPosY", travel[1]);
                tag.put("TravelPosZ", travel[2]);
                tag.remove("travel_pos");
            }
        }

        // Variants which don't exist fall back to the default one
        String variant = tag.getString("variant", null);
        if (variant != null) {
            String name = variant.startsWith("minecraft:") ? variant.substring(10) : variant;
            switch (id) {
                case "minecraft:wolf" -> {
                    if (!WOLF_VARIANTS.contains(name)) {
                        tag.put("variant", "minecraft:pale");
                        BackportSettings.count("variant reset: " + id + " " + variant);
                    }
                }
                case "minecraft:pig", "minecraft:cow", "minecraft:chicken" -> {
                    if (!FARM_VARIANTS.contains(name)) {
                        tag.put("variant", "minecraft:temperate");
                        BackportSettings.count("variant reset: " + id + " " + variant);
                    }
                }
                default -> {
                }
            }
        }

        // Hanging entities: block_pos replaced TileX / TileY / TileZ
        int[] blockPos = tag.get("block_pos") instanceof com.hivemc.chunker.nbt.tags.array.IntArrayTag array ? array.getValue() : null;
        if (blockPos != null && blockPos.length == 3 && (id.equals("minecraft:item_frame") || id.equals("minecraft:glow_item_frame")
                || id.equals("minecraft:painting") || id.equals("minecraft:leash_knot"))) {
            if (!tag.contains("TileX")) {
                tag.put("TileX", blockPos[0]);
                tag.put("TileY", blockPos[1]);
                tag.put("TileZ", blockPos[2]);
            }
            tag.remove("block_pos");
        }

        // Text displays hold a component
        if (id.equals("minecraft:text_display")) textToJson(tag, "text");
    }

    /**
     * Entities inside spawner data (spawners, spawner minecarts) are in the new format too.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void spawner(CompoundTag tag, JavaDataVersion source, JavaDataVersion target) {
        for (String key : new String[]{"SpawnData", "spawn_data"}) {
            CompoundTag data = tag.getCompound(key);
            if (data != null && data.getCompound("entity") != null) entity(data.getCompound("entity"), source, target);
        }
        for (String key : new String[]{"SpawnPotentials", "spawn_potentials"}) {
            ListTag<CompoundTag, Map<String, Tag<?>>> potentials = tag.getList(key, CompoundTag.class, null);
            if (potentials == null) continue;
            for (CompoundTag potential : potentials) {
                CompoundTag data = potential.getCompound("data");
                if (data != null && data.getCompound("entity") != null) entity(data.getCompound("entity"), source, target);
            }
        }
        // Trial spawner configs (inline once inlined)
        for (String key : new String[]{"normal_config", "ominous_config"}) {
            if (tag.get(key) instanceof CompoundTag config) spawner(config, source, target);
        }
    }

    /**
     * Downgrade a block entity compound, after everything else has written to it.
     */
    public static void blockEntity(JavaResolvers writer, CompoundTag tag, JavaDataVersion source) {
        if (!applies(source, writer.dataVersion())) return;
        JavaDataVersion target = writer.dataVersion();
        String id = tag.getString("id", "");

        // Mod formats (Create tracks, fluids, ...)
        ModCompat.blockEntity(tag);
        id = tag.getString("id", "");

        // Copper chests become plain chests (the block is mapped to minecraft:chest), Items are kept
        if (id.matches("minecraft:(waxed_)?(exposed_|weathered_|oxidized_)?copper_chest")) {
            tag.put("id", "minecraft:chest");
            id = "minecraft:chest";
            BackportSettings.count("copper chest block entities -> chest");
        }

        // Text
        jsonText(tag, "CustomName");
        jsonText(tag, "LastOutput");
        for (String side : new String[]{"front_text", "back_text"}) {
            CompoundTag text = tag.getCompound(side);
            if (text == null) continue;
            for (String key : new String[]{"messages", "filtered_messages"}) {
                if (!(text.get(key) instanceof ListTag<?, ?> messages)) continue;
                ListTag<StringTag, String> converted = new ListTag<>(TagType.STRING, messages.size());
                for (Tag<?> message : messages) {
                    converted.add(new StringTag(jsonString(message)));
                }
                text.put(key, converted);
            }
        }

        // Data components on the block entity (item_name, banner_patterns, ...)
        CompoundTag components = tag.getCompound("components");
        if (components != null) {
            tag.remove("components");
            if (components.size() > 0) {
                // Run the components through the item downgrade on a stand-in item
                CompoundTag stand = new CompoundTag(3);
                stand.put("id", "minecraft:stone");
                stand.put("count", 1);
                stand.put("components", components);
                CompoundTag converted = ItemDowngrade.item(stand, source, target);
                CompoundTag result = converted == null ? null : converted.getCompound("components");
                if (result != null && result.size() > 0) tag.put("components", result);
            }
        }

        // Trial spawners: the configs are inline in 1.21.1
        if (id.equals("minecraft:trial_spawner")) TrialSpawnerConfigs.inline(tag);

        // Lock was a plain string
        if (tag.get("lock") instanceof CompoundTag) tag.remove("lock");

        // Furnaces: new timers next to the old ones Chunker writes
        if (tag.contains("BurnTime")) {
            tag.remove("lit_time_remaining");
            tag.remove("lit_total_time");
            tag.remove("cooking_time_spent");
            tag.remove("cooking_total_time");
        }

        // Entities stored in block entities
        spawner(tag, source, target);
        ListTag<CompoundTag, Map<String, Tag<?>>> bees = tag.getList("bees", CompoundTag.class, null);
        if (bees != null) {
            for (CompoundTag bee : bees) {
                CompoundTag entity = bee.getCompound("entity_data");
                if (entity != null) entity(entity, source, target);
            }
        }
        BackportSettings.count("block entities downgraded");
    }

    /**
     * A text component as the JSON string 1.21.1 stores. Strings which already are JSON (written by Chunker) are left.
     */
    private static String jsonString(Tag<?> tag) {
        if (tag instanceof StringTag string) {
            String value = string.getValue();
            if (value.startsWith("{") && value.endsWith("}") || value.startsWith("[") && value.endsWith("]") || value.startsWith("\"") && value.endsWith("\"")) {
                return value;
            }
            return JsonTextUtil.toJSON(JsonTextUtil.fromText(value));
        }
        return JsonTextUtil.toJSON(JsonTextUtil.fromNBT(tag));
    }

    private static void jsonText(CompoundTag tag, String key) {
        Tag<?> text = tag.get(key);
        if (text != null) tag.put(key, jsonString(text));
    }
}
