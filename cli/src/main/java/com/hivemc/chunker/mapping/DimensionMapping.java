package com.hivemc.chunker.mapping;

import com.hivemc.chunker.conversion.intermediate.column.biome.ChunkerBiome;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;

/**
 * Custom Dimension definitions for a world.
 */
public record DimensionMapping(
        String identifier,
        int javaID,
        int bedrockID,
        int biomeHeight,
        ChunkerBiome.ChunkerVanillaBiome fallbackBiome
) {
    /**
     * Creates a registerable dimension based on the mapping
     * @return The dimension
     */
    public Dimension toDimension() {
        return new Dimension(javaID, bedrockID, identifier, fallbackBiome, biomeHeight);
    }
}
