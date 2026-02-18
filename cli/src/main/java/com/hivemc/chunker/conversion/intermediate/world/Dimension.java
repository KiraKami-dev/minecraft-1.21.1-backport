package com.hivemc.chunker.conversion.intermediate.world;

import com.hivemc.chunker.conversion.intermediate.column.biome.ChunkerBiome;

import java.io.File;

/**
 * The different types of Dimensions present in Minecraft.
 */
public class Dimension {
    public static Dimension OVERWORLD = new Dimension(0, 0, "minecraft:overworld", ChunkerBiome.ChunkerVanillaBiome.PLAINS, 24);
    public static Dimension NETHER = new Dimension(-1, 1, "minecraft:the_nether", ChunkerBiome.ChunkerVanillaBiome.NETHER_WASTES, 8);
    public static Dimension THE_END = new Dimension(1, 2, "minecraft:the_end", ChunkerBiome.ChunkerVanillaBiome.THE_END, 16);

    private final int javaID;
    private final int bedrockID;
    private final int biomeHeight;
    private final String identifier;
    private final ChunkerBiome.ChunkerVanillaBiome fallbackBiome;

    /**
     * Create a new dimension.
     *
     * @param javaID        the java ID.
     * @param bedrockID     the bedrock ID.
     * @param identifier    the namespaced identifier used on Java.
     * @param fallbackBiome the fallback biome for the dimension
     * @param biomeHeight   the height in chunks of how many biome palettes should be written
     */
    public Dimension(int javaID, int bedrockID, String identifier, ChunkerBiome.ChunkerVanillaBiome fallbackBiome, int biomeHeight) {
        this.javaID = javaID;
        this.bedrockID = bedrockID;
        this.identifier = identifier;
        this.fallbackBiome = fallbackBiome;
        this.biomeHeight = biomeHeight;
    }

    /**
     * The Java ID for the dimension.
     *
     * @return the ID based on 0 being overworld and -1 being the nether.
     */
    public int getJavaID() {
        return javaID;
    }

    /**
     * The Bedrock ID for the dimension.
     *
     * @return the indexed ID for the dimension.
     */
    public int getBedrockID() {
        return bedrockID;
    }

    /**
     * Get the namespaced identifier for the dimension.
     *
     * @return the namespaced identifier.
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The Fallback Vanilla Biome for the dimension.
     *
     * @return the Fallback Vanilla Biome for the dimension.
     */
    public ChunkerBiome.ChunkerVanillaBiome getFallbackBiome() {
        return fallbackBiome;
    }

    /**
     * Get the height in chunks for how many biome palettes should be written
     *
     * @return the height in chunks of how many biome palettes should be written.
     */
    public int getBiomeHeight() {
        return biomeHeight;
    }

    public File getJavaDimensionBaseDirectory(File directory) {
        if (this.javaID == 0) {
            return directory;
        } else {
            return new File(directory, "DIM" + this.javaID);
        }
    }
}
