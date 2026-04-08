package com.hivemc.chunker.conversion.intermediate.column.biome;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A type which represents a Minecraft biome.
 */
public interface ChunkerBiome {
    /**
     * A fallback if the biome is not supported on the output format.
     *
     * @return null if there is no fallback, a fallback is used when biomes are discontinued and have a suitable
     * replacement.
     */
    @Nullable
    ChunkerVanillaBiome getFallback();

    /**
     * List of vanilla biome IDs. Deprecated values indicate lossy conversion in future versions.
     */
    enum ChunkerVanillaBiome implements ChunkerBiome {
        OCEAN,
        PLAINS,
        DESERT,
        WINDSWEPT_HILLS,
        FOREST,
        TAIGA,
        SWAMP,
        RIVER,
        NETHER_WASTES,
        THE_END,
        FROZEN_OCEAN,
        FROZEN_RIVER,
        SNOWY_PLAINS,
        MUSHROOM_FIELDS,
        BEACH,
        JUNGLE,
        SPARSE_JUNGLE,
        DEEP_OCEAN,
        STONY_SHORE,
        SNOWY_BEACH,
        BIRCH_FOREST,
        DARK_FOREST,
        SNOWY_TAIGA,
        OLD_GROWTH_PINE_TAIGA,
        WINDSWEPT_FOREST,
        SAVANNA,
        SAVANNA_PLATEAU,
        BADLANDS,
        WOODED_BADLANDS,
        SMALL_END_ISLANDS,
        END_MIDLANDS,
        END_HIGHLANDS,
        END_BARRENS,
        WARM_OCEAN,
        LUKEWARM_OCEAN,
        COLD_OCEAN,
        DEEP_LUKEWARM_OCEAN,
        DEEP_COLD_OCEAN,
        DEEP_FROZEN_OCEAN,
        THE_VOID,
        SUNFLOWER_PLAINS,
        FLOWER_FOREST,
        ICE_SPIKES,
        OLD_GROWTH_BIRCH_FOREST,
        OLD_GROWTH_SPRUCE_TAIGA,
        WINDSWEPT_GRAVELLY_HILLS,
        WINDSWEPT_SAVANNA,
        ERODED_BADLANDS,
        BAMBOO_JUNGLE,
        SOUL_SAND_VALLEY,
        CRIMSON_FOREST,
        WARPED_FOREST,
        BASALT_DELTAS,
        DRIPSTONE_CAVES,
        LUSH_CAVES,
        MEADOW,
        GROVE,
        SNOWY_SLOPES,
        FROZEN_PEAKS,
        JAGGED_PEAKS,
        STONY_PEAKS,
        DEEP_DARK,
        MANGROVE_SWAMP,
        CHERRY_GROVE,
        PALE_GARDEN,
        SULFUR_CAVES,

        // No longer used in newer versions
        @Deprecated
        SNOWY_MOUNTAINS(SNOWY_PLAINS),
        @Deprecated
        MUSHROOM_FIELD_SHORE(MUSHROOM_FIELDS),
        @Deprecated
        DESERT_HILLS(DESERT),
        @Deprecated
        WOODED_HILLS(FOREST),
        @Deprecated
        TAIGA_HILLS(TAIGA),
        @Deprecated
        MOUNTAIN_EDGE(WINDSWEPT_HILLS),
        @Deprecated
        JUNGLE_HILLS(JUNGLE),
        @Deprecated
        BIRCH_FOREST_HILLS(BIRCH_FOREST),
        @Deprecated
        SNOWY_TAIGA_HILLS(SNOWY_TAIGA),
        @Deprecated
        GIANT_TREE_TAIGA_HILLS(OLD_GROWTH_PINE_TAIGA),
        @Deprecated
        BADLANDS_PLATEAU(BADLANDS),
        @Deprecated
        DEEP_WARM_OCEAN(WARM_OCEAN),
        @Deprecated
        DESERT_LAKES(DESERT),
        @Deprecated
        TAIGA_MOUNTAINS(TAIGA),
        @Deprecated
        SWAMP_HILLS(SWAMP),
        @Deprecated
        MODIFIED_JUNGLE(JUNGLE),
        @Deprecated
        MODIFIED_JUNGLE_EDGE(SPARSE_JUNGLE),
        @Deprecated
        TALL_BIRCH_HILLS(OLD_GROWTH_BIRCH_FOREST),
        @Deprecated
        DARK_FOREST_HILLS(DARK_FOREST),
        @Deprecated
        SNOWY_TAIGA_MOUNTAINS(SNOWY_TAIGA),
        @Deprecated
        GIANT_SPRUCE_TAIGA_HILLS(OLD_GROWTH_SPRUCE_TAIGA),
        @Deprecated
        MODIFIED_GRAVELLY_MOUNTAINS(WINDSWEPT_GRAVELLY_HILLS),
        @Deprecated
        SHATTERED_SAVANNA_PLATEAU(WINDSWEPT_SAVANNA),
        @Deprecated
        MODIFIED_WOODED_BADLANDS_PLATEAU(WOODED_BADLANDS),
        @Deprecated
        MODIFIED_BADLANDS_PLATEAU(BADLANDS),
        @Deprecated
        BAMBOO_JUNGLE_HILLS(BAMBOO_JUNGLE),
        @Deprecated
        LEGACY_FROZEN_OCEAN(FROZEN_OCEAN),
        ;

        private static final Map<String, ChunkerVanillaBiome> mapping;
        static {
            mapping = new HashMap<>();
            mapping.put("minecraft:ocean", ChunkerVanillaBiome.OCEAN);
            mapping.put("minecraft:plains", ChunkerVanillaBiome.PLAINS);
            mapping.put("minecraft:desert", ChunkerVanillaBiome.DESERT);
            mapping.put("minecraft:forest", ChunkerVanillaBiome.FOREST);
            mapping.put("minecraft:taiga", ChunkerVanillaBiome.TAIGA);
            mapping.put("minecraft:swamp", ChunkerVanillaBiome.SWAMP);
            mapping.put("minecraft:river", ChunkerVanillaBiome.RIVER);
            mapping.put("minecraft:nether_wastes", ChunkerVanillaBiome.NETHER_WASTES);
            mapping.put("minecraft:the_end", ChunkerVanillaBiome.THE_END);
            mapping.put("minecraft:frozen_ocean", ChunkerVanillaBiome.FROZEN_OCEAN);
            mapping.put("minecraft:frozen_river", ChunkerVanillaBiome.FROZEN_RIVER);
            mapping.put("minecraft:snowy_plains", ChunkerVanillaBiome.SNOWY_PLAINS);
            mapping.put("minecraft:mushroom_fields", ChunkerVanillaBiome.MUSHROOM_FIELDS);
            mapping.put("minecraft:beach", ChunkerVanillaBiome.BEACH);
            mapping.put("minecraft:windswept_hills", ChunkerVanillaBiome.WINDSWEPT_HILLS);
            mapping.put("minecraft:jungle", ChunkerVanillaBiome.JUNGLE);
            mapping.put("minecraft:sparse_jungle", ChunkerVanillaBiome.SPARSE_JUNGLE);
            mapping.put("minecraft:deep_ocean", ChunkerVanillaBiome.DEEP_OCEAN);
            mapping.put("minecraft:stony_shore", ChunkerVanillaBiome.STONY_SHORE);
            mapping.put("minecraft:snowy_beach", ChunkerVanillaBiome.SNOWY_BEACH);
            mapping.put("minecraft:birch_forest", ChunkerVanillaBiome.BIRCH_FOREST);
            mapping.put("minecraft:dark_forest", ChunkerVanillaBiome.DARK_FOREST);
            mapping.put("minecraft:snowy_taiga", ChunkerVanillaBiome.SNOWY_TAIGA);
            mapping.put("minecraft:old_growth_pine_taiga", ChunkerVanillaBiome.OLD_GROWTH_PINE_TAIGA);
            mapping.put("minecraft:windswept_forest", ChunkerVanillaBiome.WINDSWEPT_FOREST);
            mapping.put("minecraft:savanna", ChunkerVanillaBiome.SAVANNA);
            mapping.put("minecraft:savanna_plateau", ChunkerVanillaBiome.SAVANNA_PLATEAU);
            mapping.put("minecraft:badlands", ChunkerVanillaBiome.BADLANDS);
            mapping.put("minecraft:wooded_badlands", ChunkerVanillaBiome.WOODED_BADLANDS);
            mapping.put("minecraft:small_end_islands", ChunkerVanillaBiome.SMALL_END_ISLANDS);
            mapping.put("minecraft:end_midlands", ChunkerVanillaBiome.END_MIDLANDS);
            mapping.put("minecraft:end_highlands", ChunkerVanillaBiome.END_HIGHLANDS);
            mapping.put("minecraft:end_barrens", ChunkerVanillaBiome.END_BARRENS);
            mapping.put("minecraft:warm_ocean", ChunkerVanillaBiome.WARM_OCEAN);
            mapping.put("minecraft:lukewarm_ocean", ChunkerVanillaBiome.LUKEWARM_OCEAN);
            mapping.put("minecraft:cold_ocean", ChunkerVanillaBiome.COLD_OCEAN);
            mapping.put("minecraft:deep_lukewarm_ocean", ChunkerVanillaBiome.DEEP_LUKEWARM_OCEAN);
            mapping.put("minecraft:deep_cold_ocean", ChunkerVanillaBiome.DEEP_COLD_OCEAN);
            mapping.put("minecraft:deep_frozen_ocean", ChunkerVanillaBiome.DEEP_FROZEN_OCEAN);
            mapping.put("minecraft:the_void", ChunkerVanillaBiome.THE_VOID);
            mapping.put("minecraft:sunflower_plains", ChunkerVanillaBiome.SUNFLOWER_PLAINS);
            mapping.put("minecraft:windswept_gravelly_hills", ChunkerVanillaBiome.WINDSWEPT_GRAVELLY_HILLS);
            mapping.put("minecraft:flower_forest", ChunkerVanillaBiome.FLOWER_FOREST);
            mapping.put("minecraft:ice_spikes", ChunkerVanillaBiome.ICE_SPIKES);
            mapping.put("minecraft:old_growth_birch_forest", ChunkerVanillaBiome.OLD_GROWTH_BIRCH_FOREST);
            mapping.put("minecraft:old_growth_spruce_taiga", ChunkerVanillaBiome.OLD_GROWTH_SPRUCE_TAIGA);
            mapping.put("minecraft:windswept_savanna", ChunkerVanillaBiome.WINDSWEPT_SAVANNA);
            mapping.put("minecraft:eroded_badlands", ChunkerVanillaBiome.ERODED_BADLANDS);
            mapping.put("minecraft:bamboo_jungle", ChunkerVanillaBiome.BAMBOO_JUNGLE);
            mapping.put("minecraft:soul_sand_valley", ChunkerVanillaBiome.SOUL_SAND_VALLEY);
            mapping.put("minecraft:crimson_forest", ChunkerVanillaBiome.CRIMSON_FOREST);
            mapping.put("minecraft:warped_forest", ChunkerVanillaBiome.WARPED_FOREST);
            mapping.put("minecraft:basalt_deltas", ChunkerVanillaBiome.BASALT_DELTAS);
            mapping.put("minecraft:dripstone_caves", ChunkerVanillaBiome.DRIPSTONE_CAVES);
            mapping.put("minecraft:lush_caves", ChunkerVanillaBiome.LUSH_CAVES);
            mapping.put("minecraft:meadow", ChunkerVanillaBiome.MEADOW);
            mapping.put("minecraft:grove", ChunkerVanillaBiome.GROVE);
            mapping.put("minecraft:snowy_slopes", ChunkerVanillaBiome.SNOWY_SLOPES);
            mapping.put("minecraft:frozen_peaks", ChunkerVanillaBiome.FROZEN_PEAKS);
            mapping.put("minecraft:jagged_peaks", ChunkerVanillaBiome.JAGGED_PEAKS);
            mapping.put("minecraft:stony_peaks", ChunkerVanillaBiome.STONY_PEAKS);
            mapping.put("minecraft:deep_dark", ChunkerVanillaBiome.DEEP_DARK);
            mapping.put("minecraft:mangrove_swamp", ChunkerVanillaBiome.MANGROVE_SWAMP);
            mapping.put("minecraft:cherry_grove", ChunkerVanillaBiome.CHERRY_GROVE);
            mapping.put("minecraft:pale_garden", ChunkerVanillaBiome.PALE_GARDEN);
            mapping.put("minecraft:sulfur_caves", ChunkerVanillaBiome.SULFUR_CAVES);

            // Bedrock names
            mapping.put("minecraft:roofed_forest", ChunkerVanillaBiome.DARK_FOREST);
            mapping.put("minecraft:mesa_bryce", ChunkerVanillaBiome.ERODED_BADLANDS);
            mapping.put("minecraft:ice_plains_spikes", ChunkerVanillaBiome.ICE_SPIKES);
            mapping.put("minecraft:mushroom_island", ChunkerVanillaBiome.MUSHROOM_FIELDS);
            mapping.put("minecraft:birch_forest_mutated", ChunkerVanillaBiome.OLD_GROWTH_BIRCH_FOREST);
            mapping.put("minecraft:mega_taiga", ChunkerVanillaBiome.OLD_GROWTH_PINE_TAIGA);
            mapping.put("minecraft:redwood_taiga_mutated", ChunkerVanillaBiome.OLD_GROWTH_SPRUCE_TAIGA);
            mapping.put("minecraft:cold_beach", ChunkerVanillaBiome.SNOWY_BEACH);
            mapping.put("minecraft:ice_plains", ChunkerVanillaBiome.SNOWY_PLAINS);
            mapping.put("minecraft:cold_taiga", ChunkerVanillaBiome.SNOWY_TAIGA);
            mapping.put("minecraft:jungle_edge", ChunkerVanillaBiome.SPARSE_JUNGLE);
            mapping.put("minecraft:stone_beach", ChunkerVanillaBiome.STONY_SHORE);
            mapping.put("minecraft:swampland", ChunkerVanillaBiome.SWAMP);
            mapping.put("minecraft:extreme_hills_plus_trees", ChunkerVanillaBiome.WINDSWEPT_FOREST);
            mapping.put("minecraft:extreme_hills_mutated", ChunkerVanillaBiome.WINDSWEPT_GRAVELLY_HILLS);
            mapping.put("minecraft:extreme_hills", ChunkerVanillaBiome.WINDSWEPT_HILLS);
            mapping.put("minecraft:savanna_mutated", ChunkerVanillaBiome.WINDSWEPT_SAVANNA);
            mapping.put("minecraft:mesa_plateau_stone", ChunkerVanillaBiome.WOODED_BADLANDS);
            mapping.put("minecraft:hell", ChunkerVanillaBiome.NETHER_WASTES);

            mapping.put("minecraft:bamboo_jungle_hills", ChunkerVanillaBiome.BAMBOO_JUNGLE_HILLS);
            mapping.put("minecraft:birch_forest_hills", ChunkerVanillaBiome.BIRCH_FOREST_HILLS);
            mapping.put("minecraft:birch_forest_hills_mutated", ChunkerVanillaBiome.TALL_BIRCH_HILLS);
            mapping.put("minecraft:cold_taiga_hills", ChunkerVanillaBiome.SNOWY_TAIGA_HILLS);
            mapping.put("minecraft:cold_taiga_mutated", ChunkerVanillaBiome.SNOWY_TAIGA_MOUNTAINS);
            mapping.put("minecraft:deep_warm_ocean", ChunkerVanillaBiome.DEEP_WARM_OCEAN);
            mapping.put("minecraft:desert_hills", ChunkerVanillaBiome.DESERT_HILLS);
            mapping.put("minecraft:desert_mutated", ChunkerVanillaBiome.DESERT_LAKES);
            mapping.put("minecraft:extreme_hills_edge", ChunkerVanillaBiome.MOUNTAIN_EDGE);
            mapping.put("minecraft:extreme_hills_plus_trees_mutated", ChunkerVanillaBiome.MODIFIED_GRAVELLY_MOUNTAINS);
            mapping.put("minecraft:forest_hills", ChunkerVanillaBiome.WOODED_HILLS);
            mapping.put("minecraft:ice_mountains", ChunkerVanillaBiome.SNOWY_MOUNTAINS);
            mapping.put("minecraft:jungle_edge_mutated", ChunkerVanillaBiome.MODIFIED_JUNGLE_EDGE);
            mapping.put("minecraft:jungle_hills", ChunkerVanillaBiome.JUNGLE_HILLS);
            mapping.put("minecraft:jungle_mutated", ChunkerVanillaBiome.MODIFIED_JUNGLE);
            mapping.put("minecraft:legacy_frozen_ocean", ChunkerVanillaBiome.LEGACY_FROZEN_OCEAN);
            mapping.put("minecraft:mega_taiga_hills", ChunkerVanillaBiome.GIANT_TREE_TAIGA_HILLS);
            mapping.put("minecraft:mesa", ChunkerVanillaBiome.BADLANDS);
            mapping.put("minecraft:mesa_plateau", ChunkerVanillaBiome.BADLANDS_PLATEAU);
            mapping.put("minecraft:mesa_plateau_mutated", ChunkerVanillaBiome.MODIFIED_BADLANDS_PLATEAU);
            mapping.put("minecraft:mesa_plateau_stone_mutated", ChunkerVanillaBiome.MODIFIED_WOODED_BADLANDS_PLATEAU);
            mapping.put("minecraft:mushroom_island_shore", ChunkerVanillaBiome.MUSHROOM_FIELD_SHORE);
            mapping.put("minecraft:redwood_taiga_hills_mutated", ChunkerVanillaBiome.GIANT_SPRUCE_TAIGA_HILLS);
            mapping.put("minecraft:roofed_forest_mutated", ChunkerVanillaBiome.DARK_FOREST_HILLS);
            mapping.put("minecraft:savanna_plateau_mutated", ChunkerVanillaBiome.SHATTERED_SAVANNA_PLATEAU);
            mapping.put("minecraft:soulsand_valley", ChunkerVanillaBiome.SOUL_SAND_VALLEY);
            mapping.put("minecraft:swampland_mutated", ChunkerVanillaBiome.SWAMP_HILLS);
            mapping.put("minecraft:taiga_hills", ChunkerVanillaBiome.TAIGA_HILLS);
            mapping.put("minecraft:taiga_mutated", ChunkerVanillaBiome.TAIGA_MOUNTAINS);
        }

        /**
         * Find vanilla biome by name
         * @param value The string name of the biome
         * @return Biome if found otherwise empty optional
         */
        public static Optional<ChunkerVanillaBiome> find(String value) {
            return Optional.ofNullable(mapping.get(value));
        }

        private final ChunkerVanillaBiome fallback;

        /**
         * Create a Vanilla biome.
         *
         * @param fallback the fallback to use if this biome isn't supported, this should be used when a biome has been
         *                 split or merged.
         */
        ChunkerVanillaBiome(ChunkerVanillaBiome fallback) {
            this.fallback = fallback;
        }

        /**
         * Create a Vanilla biome with no fallback.
         */
        ChunkerVanillaBiome() {
            this(null);
        }

        @Override
        public ChunkerVanillaBiome getFallback() {
            return fallback;
        }
    }
}
