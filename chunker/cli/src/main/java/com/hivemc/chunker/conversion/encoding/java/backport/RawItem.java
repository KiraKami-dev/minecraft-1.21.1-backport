package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;

/**
 * The item NBT exactly as it was read, so a backport can convert it without going through the lossy item model.
 *
 * @param tag    the original item compound.
 * @param source the data version it was read from.
 */
public record RawItem(CompoundTag tag, JavaDataVersion source) {
    @Override
    public boolean equals(Object o) {
        return o instanceof RawItem other && tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }
}
