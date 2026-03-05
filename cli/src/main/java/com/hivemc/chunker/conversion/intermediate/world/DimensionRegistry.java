package com.hivemc.chunker.conversion.intermediate.world;

import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

/**
 * Registry of dimensions that are registered to this world or the output world
 */
public class DimensionRegistry {
    private final HashMap<String, Dimension> dimensionByIdentifier = new HashMap<>();
    private final Int2ObjectMap<Dimension> dimensionByJavaId = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Dimension> dimensionByBedrockId = new Int2ObjectOpenHashMap<>();

    /**
     * Creates a new DimensionRegistry with vanilla dimensions by default
     */
    public DimensionRegistry() {
        register(Dimension.OVERWORLD.getIdentifier(), Dimension.OVERWORLD);
        register(Dimension.NETHER.getIdentifier(), Dimension.NETHER);
        register(Dimension.THE_END.getIdentifier(), Dimension.THE_END);
    }

    /**
     * Get the dimensions registered
     * @return Collection of Dimensions registered
     */
    public Collection<Dimension> getDimensions() {
        return dimensionByIdentifier.values();
    }

    /**
     * Registers a dimension
     * @param identifier the identifier of the Dimension
     * @param dimension  the dimension to be added
     */
    public void register(String identifier, Dimension dimension) {
        this.dimensionByIdentifier.put(identifier, dimension);
        this.dimensionByBedrockId.put(dimension.getBedrockID(), dimension);
        this.dimensionByJavaId.put(dimension.getJavaID(), dimension);
    }

    /**
     * Finds a dimension by it's registered identifier
     * @param identifier the identifier of the Dimension to be found
     * @return The dimension if found, null otherwise
     */
    public @Nullable Dimension getByIdentifier(String identifier) {
        return dimensionByIdentifier.get(identifier);
    }

    /**
     * Get the dimension based on a Java NBT tag.
     *
     * @param tag      the tag to use (string, byte or integer).
     * @param fallback the fallback dimension to use if the tag can't be parsed or the ID is invalid.
     * @return the dimension if it was parsed otherwise the fallback.
     */
    public Dimension fromJavaNBT(@Nullable Tag<?> tag, Dimension fallback) {
        // There is every chance there was no tag, so we'll handle that first
        if (tag == null) return fallback;

        // We can either parse it as an identifier or a byte
        try {
            if (tag instanceof StringTag stringTag) {
                return dimensionByIdentifier.getOrDefault(Objects.requireNonNull(stringTag.getValue()).toLowerCase(Locale.ROOT), fallback);
            } else if (tag instanceof ByteTag byteTag) {
                byte value = byteTag.getValue();
                return fromJava(value, fallback);
            } else if (tag instanceof IntTag intTag) {
                byte value = (byte) intTag.getValue();
                return fromJava(value, fallback);
            } else {
                return fallback; // Can't be parsed
            }
        } catch (Exception exception) {
            return fallback;
        }
    }

    /**
     * Get the dimension based on a Bedrock NBT tag.
     *
     * @param tag      the tag to use (string, byte or integer).
     * @param fallback the fallback dimension to use if the tag can't be parsed or the ID is invalid.
     * @return the dimension if it was parsed otherwise the fallback.
     */
    public Dimension fromBedrockNBT(@Nullable Tag<?> tag, Dimension fallback) {
        // There is every chance there was no tag, so we'll handle that first
        if (tag == null) return fallback;

        // We can either parse it as an identifier or a byte
        try {
            if (tag instanceof StringTag stringTag) {
                return dimensionByIdentifier.getOrDefault(Objects.requireNonNull(stringTag.getValue()).toLowerCase(Locale.ROOT), fallback);
            } else if (tag instanceof ByteTag byteTag) {
                int value = byteTag.getValue();
                return dimensionByBedrockId.getOrDefault(value, fallback);
            } else if (tag instanceof IntTag intTag) {
                int value = intTag.getValue();
                return dimensionByBedrockId.getOrDefault(value, fallback);
            } else {
                return fallback; // Can't be parsed
            }
        } catch (Exception exception) {
            return fallback;
        }
    }

    /**
     * Create a dimension from a Bedrock ID.
     *
     * @param id       the input ID.
     * @param fallback the fallback to use if the ID wasn't found.
     * @return the dimension or fallback if it wasn't found.
     */
    public Dimension fromBedrock(int id, Dimension fallback) {
        return dimensionByBedrockId.getOrDefault(id, fallback);
    }

    /**
     * Create a dimension from a Java ID.
     *
     * @param id       the input ID.
     * @param fallback the fallback to use if the ID wasn't found.
     * @return the dimension or fallback if it wasn't found.
     */
    public Dimension fromJava(int id, Dimension fallback) {
        return dimensionByJavaId.getOrDefault(id, fallback);
    }
}
