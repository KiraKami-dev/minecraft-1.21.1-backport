package com.hivemc.chunker.conversion.encoding.java.backport;

import java.util.HashMap;
import java.util.Map;

/**
 * Biomes O' Plenty ids (items and blocks) that exist in the 1.21.11 version of the mod but not in the 1.21.1 one.
 * <p>
 * Checked against the 1.21.1 jar (downloads/ids/biomes-o-plenty.jar). The same map serves items and blocks, the
 * block agent can use it for block ids (block states need their own handling, e.g. the leaf pile layers).
 * A value of {@link #DROP} means the id has no equivalent and should be removed.
 */
public final class BopItemMap {
    public static final String DROP = "";
    private static final Map<String, String> MAP = new HashMap<>();

    static {
        // Leaf litter was called leaf pile in 1.21.1
        for (String colour : new String[]{"red", "orange", "yellow"}) {
            MAP.put("biomesoplenty:" + colour + "_maple_leaf_litter", "biomesoplenty:" + colour + "_maple_leaf_pile");
        }
        // Flowers added later
        MAP.put("biomesoplenty:purple_wildflowers", "biomesoplenty:violet");
        MAP.put("biomesoplenty:marigold", "biomesoplenty:orange_cosmos");
        // Origin oak variants (worldgen only blocks)
        MAP.put("biomesoplenty:origin_oak_leaves", "minecraft:oak_leaves");
        MAP.put("biomesoplenty:origin_oak_log", "minecraft:oak_log");
        MAP.put("biomesoplenty:origin_rose", "minecraft:poppy");
        MAP.put("biomesoplenty:origin_dandelion", "minecraft:dandelion");
        // Giant flower petal blocks become a flower of the same colour
        petal("red", "biomesoplenty:rose");
        petal("orange", "biomesoplenty:orange_cosmos");
        petal("yellow", "minecraft:dandelion");
        petal("pink", "biomesoplenty:pink_hibiscus");
        petal("purple", "biomesoplenty:violet");
        petal("magenta", "minecraft:allium");
        petal("blue", "minecraft:cornflower");
        petal("light_blue", "minecraft:blue_orchid");
        petal("cyan", "minecraft:blue_orchid");
        petal("white", "minecraft:oxeye_daisy");
        petal("light_gray", "minecraft:azure_bluet");
        for (String colour : new String[]{"green", "lime", "brown", "gray", "black"}) petal(colour, DROP);
        MAP.put("biomesoplenty:flower_stem", DROP);
        MAP.put("biomesoplenty:flower_bud", DROP);
    }

    private BopItemMap() {
    }

    private static void petal(String colour, String flower) {
        MAP.put("biomesoplenty:" + colour + "_flower_petal_block", flower);
    }

    /**
     * Find the replacement for a BOP id.
     *
     * @param id the namespaced id.
     * @return the replacement id, {@link #DROP} if it should be removed or null if the id doesn't need changing.
     */
    public static String map(String id) {
        return MAP.get(id);
    }

    public static Map<String, String> all() {
        return Map.copyOf(MAP);
    }
}
