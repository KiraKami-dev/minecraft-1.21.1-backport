package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.ShortTag;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item ids that don't exist on the target server and are swapped for something that does.
 * Every substitution is counted and can be written to a log file.
 */
public final class ItemSubstitutions {
    private static final String[] TIERS = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
    private static final Pattern EXPANDED_ARMOR = Pattern.compile("expanded_weaponry:heavy_([a-z]+)_(helmet|chestplate|leggings|boots)");
    private static final Pattern EXPANDED_TIERED = Pattern.compile("expanded_weaponry:([a-z]+)_([a-z_]+)");
    private static final Pattern COPPER_CHEST = Pattern.compile("minecraft:(waxed_)?((exposed|weathered|oxidized)_)?copper_chest");
    private static final Map<String, AtomicLong> STACKS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> ITEMS = new ConcurrentHashMap<>();
    // Vanilla durabilities for clamping damage when a heavier modded item turns into a vanilla one
    private static final Map<String, Integer> MAX_DAMAGE = new ConcurrentHashMap<>();

    static {
        int[] sword = {59, 131, 190, 250, 32, 1561, 2031};
        String[] swordTiers = {"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"};
        for (int i = 0; i < swordTiers.length; i++) MAX_DAMAGE.put("minecraft:" + swordTiers[i] + "_sword", sword[i]);
        // Armor: base durability multiplier by tier, times 11/16/15/13
        String[] armorTiers = {"leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite"};
        int[] multiplier = {5, 15, 11, 15, 7, 33, 37};
        int[] parts = {11, 16, 15, 13};
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < armorTiers.length; i++) {
            for (int p = 0; p < 4; p++) {
                MAX_DAMAGE.put("minecraft:" + armorTiers[i] + "_" + names[p], multiplier[i] * parts[p]);
            }
        }
        MAX_DAMAGE.put("minecraft:bow", 384);
        MAX_DAMAGE.put("minecraft:crossbow", 465);
    }

    private ItemSubstitutions() {
    }

    /**
     * Work out what an item id should become.
     *
     * @param id the namespaced item id.
     * @return the new id, {@link BopItemMap#DROP} to remove the item, or null if the id is kept.
     */
    public static String replacement(String id) {
        switch (id) {
            case "minecraft:iron_chain":
                return "minecraft:chain";
            case "universal_graves:grave_compass":
                return "minecraft:compass";
            default:
                break;
        }
        if (COPPER_CHEST.matcher(id).matches()) return "minecraft:chest";
        if (id.startsWith("biomesoplenty:")) return BopItemMap.map(id);
        if (id.startsWith("expanded_weaponry:")) return expandedWeaponry(id);
        return null;
    }

    private static String expandedWeaponry(String id) {
        Matcher armor = EXPANDED_ARMOR.matcher(id);
        if (armor.matches()) return "minecraft:" + armor.group(1) + "_" + armor.group(2);
        switch (id) {
            case "expanded_weaponry:longbow", "expanded_weaponry:shortbow", "expanded_weaponry:slingshot":
                return "minecraft:bow";
            case "expanded_weaponry:heavy_crossbow", "expanded_weaponry:arbalest":
                return "minecraft:crossbow";
            default:
                break;
        }
        Matcher tiered = EXPANDED_TIERED.matcher(id);
        if (tiered.matches()) {
            String tier = tiered.group(1);
            boolean known = false;
            for (String t : TIERS) known |= t.equals(tier);
            if (!known) return null;
            String kind = tiered.group(2);
            if (kind.equals("spear")) return "minecraft:" + tier + "_spear";
            if (kind.equals("pickaxe") || kind.equals("axe") || kind.equals("shovel") || kind.equals("hoe")) {
                return "minecraft:" + tier + "_" + kind;
            }
            // dagger, hammer, greatsword, twin blade sword, katana ... all melee weapons
            return "minecraft:" + tier + "_sword";
        }
        return null;
    }

    /**
     * Substitute the id of an item compound in place (id, damage and components of the old mod).
     *
     * @return false if the item should be dropped.
     */
    public static boolean apply(CompoundTag item) {
        String id = item.getString("id", "");
        String replacement = replacement(id);
        if (replacement == null) return true;

        long count = 1;
        Tag<?> countTag = item.get("count");
        if (countTag instanceof IntTag i) count = i.getValue();
        else if (countTag instanceof ByteTag b) count = b.getValue();
        else if (countTag instanceof ShortTag s) count = s.getValue();

        STACKS.computeIfAbsent(id + " -> " + (replacement.isEmpty() ? "(dropped)" : replacement), k -> new AtomicLong()).incrementAndGet();
        ITEMS.computeIfAbsent(id + " -> " + (replacement.isEmpty() ? "(dropped)" : replacement), k -> new AtomicLong()).addAndGet(count);
        if (replacement.isEmpty()) return false;

        item.put("id", replacement);
        CompoundTag components = item.getCompound("components");
        if (components != null) {
            String namespace = id.substring(0, id.indexOf(':') + 1);
            // Components of the mod the item came from mean nothing to the vanilla item
            for (String key : components.getValue().keySet().toArray(new String[0])) {
                String base = key.startsWith("!") ? key.substring(1) : key;
                if (base.startsWith(namespace) || base.startsWith("universal_graves:")) components.remove(key);
            }
            Integer max = MAX_DAMAGE.get(replacement);
            if (max != null && components.get("minecraft:damage") instanceof IntTag damage && damage.getValue() >= max) {
                components.put("minecraft:damage", max - 1);
            }
            if (components.size() == 0) item.remove("components");
        }
        return true;
    }

    public static void writeLog(File file) throws IOException {
        Map<String, AtomicLong> stacks = new TreeMap<>(STACKS);
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            writer.println("# item substitutions: original id -> new id, stacks, total item count");
            for (Map.Entry<String, AtomicLong> entry : stacks.entrySet()) {
                writer.println(entry.getKey() + "\tstacks=" + entry.getValue().get() + "\titems=" + ITEMS.get(entry.getKey()).get());
            }
            writer.println();
            writer.println("# conversion report");
            for (Map.Entry<String, Long> entry : BackportSettings.getReport().entrySet()) {
                writer.println(entry.getKey() + "\t" + entry.getValue());
            }
        }
    }
}
