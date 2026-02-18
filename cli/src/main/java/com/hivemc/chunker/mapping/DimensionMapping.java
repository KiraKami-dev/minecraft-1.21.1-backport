package com.hivemc.chunker.mapping;

import com.hivemc.chunker.conversion.intermediate.column.biome.ChunkerBiome;
import com.hivemc.chunker.conversion.intermediate.world.Dimension;

public record DimensionMapping(
        String identifier,
        int javaID,
        int bedrockID,
        int biomeHeight,
        ChunkerBiome.ChunkerVanillaBiome fallbackBiome
) {
    public Dimension toDimension() {
        return new Dimension(javaID, bedrockID, identifier, ChunkerBiome.ChunkerVanillaBiome.PLAINS, biomeHeight);
    }
}
