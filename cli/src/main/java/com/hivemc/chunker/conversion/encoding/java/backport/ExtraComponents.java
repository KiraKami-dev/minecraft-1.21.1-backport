package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;

import java.util.Map;

/**
 * Item components carried through a conversion as raw NBT, with any items / blocks nested inside them.
 *
 * @param components the original components compound.
 * @param embedded   nested items / block states captured by the reader.
 * @param source     the data version the components were read from.
 */
public record ExtraComponents(CompoundTag components, Map<Tag<?>, Object> embedded, JavaDataVersion source) {
    @Override
    public boolean equals(Object o) {
        return o instanceof ExtraComponents other && components.equals(other.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }
}
