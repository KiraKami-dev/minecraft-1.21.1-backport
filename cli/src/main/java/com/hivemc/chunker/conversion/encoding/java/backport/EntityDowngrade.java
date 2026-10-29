package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.DoubleTag;
import com.hivemc.chunker.nbt.tags.primitive.FloatTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;
import com.hivemc.chunker.util.JsonTextUtil;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the entity / player NBT fields that changed between 1.21.1 and 1.21.11 back to their 1.21.1 form.
 * Only the fields the old game would otherwise lose data on are converted, unknown new keys are ignored by it.
 */
public final class EntityDowngrade {
    private static final Pattern BOAT = Pattern.compile("minecraft:([a-z_]+?)_(chest_)?(boat|raft)");
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
                tag.put("id", matcher.group(2) != null ? "minecraft:chest_boat" : "minecraft:boat");
                tag.put("Type", matcher.group(1));
                BackportSettings.count("boat downgraded: " + id);
            }
        }

        if (applies(source, target)) {
            common(tag);
            equipment(tag, id);
            dropChances(tag);
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
        textToJson(tag, "CustomName");
        if (tag.get("fall_distance") instanceof DoubleTag fall) {
            tag.put("FallDistance", (float) (double) fall.getValue());
            tag.remove("fall_distance");
        }
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
}
