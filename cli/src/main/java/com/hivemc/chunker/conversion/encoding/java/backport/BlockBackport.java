package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.mapping.identifier.Identifier;
import com.hivemc.chunker.mapping.identifier.states.StateValue;
import com.hivemc.chunker.mapping.identifier.states.StateValueString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * Block substitutions for the modded-world backport (1.21.11 world -> 1.21.1 + Vanilla Backport + 1.21.1 mods).
 * Applied by the Java block identifier resolver when writing.
 * <ul>
 *     <li>{@link #MAP}: blocks written under another id (or air) because the target lacks them.</li>
 *     <li>Post-1.21.1 vanilla blocks which Vanilla Backport registers (resources/backport/vb-blocks.txt) are written
 *     as-is under the same id.</li>
 *     <li>Any other post-1.21.1 vanilla block is looked up in {@link #VANILLA_FALLBACK}, else dropped (air).</li>
 * </ul>
 */
public final class BlockBackport {
    public static final String AIR = "minecraft:air";

    // Substitution table: source block id -> replacement. Keep this the single place to edit block remaps.
    // AIR means the block is removed. States are dropped unless the rule says otherwise.
    private static final Map<String, UnaryOperator<Identifier>> MAP = new LinkedHashMap<>();

    // Post-1.21.1 vanilla blocks (neither in 1.21.1 nor in Vanilla Backport) -> nearest 1.21.1 block.
    private static final Map<String, String> VANILLA_FALLBACK = new LinkedHashMap<>();

    static {
        // --- vanilla ---
        rule("minecraft:iron_chain", "minecraft:chain", true);

        // --- Biomes O' Plenty: blocks added after the 1.21.1 release ---
        // Maple leaf litter (BOP 1.21.1 has maple_leaf_pile, a different block, so these are removed)
        rule("biomesoplenty:red_maple_leaf_litter", AIR, false);
        rule("biomesoplenty:orange_maple_leaf_litter", AIR, false);
        rule("biomesoplenty:yellow_maple_leaf_litter", AIR, false);
        rule("biomesoplenty:purple_wildflowers", "biomesoplenty:violet", false);
        rule("biomesoplenty:flower_stem", AIR, false);
        rule("biomesoplenty:flower_bud", AIR, false);
        // Flower petal blocks by colour: single-block flowers only (double flowers would break); no match -> air
        petals("red", "biomesoplenty:rose");
        petals("orange", "biomesoplenty:orange_cosmos");
        petals("yellow", "minecraft:dandelion");
        petals("pink", "biomesoplenty:pink_hibiscus");
        petals("purple", "biomesoplenty:violet");
        petals("magenta", "minecraft:allium");
        petals("blue", "minecraft:cornflower");
        petals("light_blue", "minecraft:blue_orchid");
        petals("cyan", "minecraft:blue_orchid");
        petals("white", "minecraft:oxeye_daisy");
        petals("light_gray", "minecraft:azure_bluet");
        petals("green", AIR);
        petals("lime", AIR);
        petals("brown", AIR);
        petals("gray", AIR);
        petals("black", AIR);
        rule("biomesoplenty:marigold", "biomesoplenty:orange_cosmos", false);
        // Origin variants
        rule("biomesoplenty:origin_oak_leaves", "minecraft:oak_leaves", true);
        rule("biomesoplenty:origin_oak_log", "minecraft:oak_log", true);
        rule("biomesoplenty:origin_rose", "minecraft:poppy", false);
        rule("biomesoplenty:origin_dandelion", "minecraft:dandelion", false);

        // --- Universal Graves: grave -> chest (rotation 0-15 -> nearest facing, waterlogged kept) ---
        MAP.put("universal_graves:grave", BlockBackport::graveToChest);

        // --- nearest equivalents for new vanilla blocks without a Vanilla Backport counterpart ---
        for (String prefix : new String[]{"", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"}) {
            VANILLA_FALLBACK.put("minecraft:" + prefix + "copper_chest", "minecraft:chest");
        }
    }

    // Modded biomes that don't exist in the 1.21.1 version of the mod -> nearest biome there.
    private static final Map<String, String> BIOME_MAP = Map.of(
            "biomesoplenty:subtropics", "biomesoplenty:tropics",
            "biomesoplenty:end_flats", "minecraft:the_end"
    );

    private static volatile Set<String> vbBlocks;

    private BlockBackport() {
    }

    /**
     * Whether the block backport is on (the --backport settings are loaded, -Dchunker.backport=file, or -Dchunker.backport.blocks=true).
     */
    public static boolean active() {
        return BackportSettings.isEnabled() || Boolean.getBoolean("chunker.backport.blocks")
                || System.getProperty("chunker.backport") != null;
    }

    private static void rule(String from, String to, boolean keepStates) {
        MAP.put(from, input -> new Identifier(to, keepStates ? input.getStates() : Collections.emptyMap()));
    }

    private static void petals(String colour, String to) {
        rule("biomesoplenty:" + colour + "_flower_petal_block", to, false);
    }

    private static Identifier graveToChest(Identifier input) {
        Map<String, StateValue<?>> out = new HashMap<>();
        int rotation = 0;
        StateValue<?> rot = input.getStates().get("rotation");
        if (rot != null) {
            try {
                rotation = Integer.parseInt(String.valueOf(rot.getBoxed()));
            } catch (NumberFormatException ignored) {
            }
        }
        // Rotation is in 22.5 degree steps with 0 = south, 4 = west, 8 = north, 12 = east
        out.put("facing", new StateValueString(new String[]{"south", "west", "north", "east"}[(((rotation + 2) / 4) % 4 + 4) % 4]));
        out.put("type", new StateValueString("single"));
        StateValue<?> waterlogged = input.getStates().get("waterlogged");
        if (waterlogged != null) out.put("waterlogged", waterlogged);
        return new Identifier("minecraft:chest", out);
    }

    /**
     * Map a modded biome id to one which exists in the target mod version.
     */
    public static String remapBiome(String id) {
        return BIOME_MAP.getOrDefault(id, id);
    }

    /**
     * Apply the substitution table to a written identifier.
     */
    public static Identifier remap(Identifier written) {
        UnaryOperator<Identifier> rule = MAP.get(written.getIdentifier());
        if (rule == null) return written;
        return rule.apply(written);
    }

    /**
     * Handle a vanilla block which the target version's mappings could not write.
     *
     * @param newer the block as written for the newest version (1.21.11).
     * @return the identifier to write or empty if the block should be dropped.
     */
    public static Optional<Identifier> remapNewerVanilla(Identifier newer) {
        String id = newer.getIdentifier();
        if (getVanillaBackportBlocks().contains(id)) {
            return Optional.of(newer);
        }
        String fallback = VANILLA_FALLBACK.get(id);
        if (fallback != null) {
            return Optional.of(new Identifier(fallback, Collections.emptyMap()));
        }
        BackportSettings.count("block dropped (new vanilla, no equivalent): " + id);
        return Optional.empty();
    }

    /**
     * The blocks Vanilla Backport registers.
     */
    public static Set<String> getVanillaBackportBlocks() {
        Set<String> result = vbBlocks;
        if (result == null) {
            Set<String> loaded = new HashSet<>();
            try (InputStream in = BlockBackport.class.getResourceAsStream("/backport/vb-blocks.txt")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) loaded.add(line.trim());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read vb-blocks.txt", e);
            }
            vbBlocks = result = Collections.unmodifiableSet(loaded);
        }
        return result;
    }
}
