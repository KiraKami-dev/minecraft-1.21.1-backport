package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.base.resolver.JavaResolvers;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.ChunkerBlockIdentifier;
import com.hivemc.chunker.conversion.intermediate.column.chunk.itemstack.ChunkerItemStack;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.IntTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts item stacks and block states that sit inside NBT Chunker otherwise copies verbatim
 * (modded block entities, entities, player data, saved data). Items/blocks are parsed with the reader's
 * resolvers and written again with the writer's, keyed by the identity of the compound they came from.
 */
public final class EmbeddedNBT {
    // Keys that belong to the item stack itself, everything else on the compound (Slot, Mask, ...) is kept
    private static final Set<String> ITEM_KEYS = Set.of("id", "count", "components", "Count", "tag", "Damage");
    private static final Set<String> BLOCK_KEYS = Set.of("Name", "Properties");

    private EmbeddedNBT() {
    }

    public static Map<Tag<?>, Object> read(JavaResolvers resolvers, Tag<?> root) {
        Map<Tag<?>, Object> found = new IdentityHashMap<>();
        collect(resolvers, root, found, true);
        return found;
    }

    private static void collect(JavaResolvers resolvers, Tag<?> tag, Map<Tag<?>, Object> found, boolean root) {
        if (tag instanceof CompoundTag compoundTag) {
            if (!root && isItem(compoundTag)) {
                ChunkerItemStack itemStack = resolvers.readItem(compoundTag);
                if (!ChunkerBlockIdentifier.AIR.equals(itemStack.getIdentifier())) {
                    found.put(compoundTag, itemStack);
                } else {
                    BackportSettings.count("embedded item unreadable: " + compoundTag.getString("id", "?"));
                }
                return;
            }
            if (!root && isBlockState(compoundTag)) {
                try {
                    ChunkerBlockIdentifier block = resolvers.readBlock(compoundTag);
                    if (!block.isAir() || compoundTag.getString("Name", "").endsWith(":air")) {
                        found.put(compoundTag, block);
                    }
                } catch (Exception e) {
                    BackportSettings.count("embedded block unreadable: " + compoundTag.getString("Name", "?"));
                }
                return;
            }
            for (Map.Entry<String, Tag<?>> entry : compoundTag) {
                collect(resolvers, entry.getValue(), found, false);
            }
        } else if (tag instanceof ListTag<?, ?> listTag) {
            for (Tag<?> child : listTag) {
                collect(resolvers, child, found, false);
            }
        }
    }

    public static boolean isItem(CompoundTag tag) {
        return tag.get("id") instanceof StringTag id && id.getValue().indexOf(':') > 0
                && (tag.get("count") instanceof IntTag || tag.get("count") instanceof ByteTag)
                && !tag.contains("Pos");
    }

    private static boolean isBlockState(CompoundTag tag) {
        if (!(tag.get("Name") instanceof StringTag name) || name.getValue().indexOf(':') <= 0) return false;
        if (tag.contains("Properties")) return tag.get("Properties") instanceof CompoundTag;
        return tag.size() == 1;
    }

    /**
     * Produce a copy of tag with every captured item / block re-written using the writer's resolvers.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Tag<?> rewrite(JavaResolvers resolvers, Tag<?> tag, Map<Tag<?>, Object> found) {
        Object captured = found.isEmpty() ? null : found.get(tag);
        if (captured instanceof ChunkerItemStack itemStack) {
            Optional<CompoundTag> written = resolvers.writeItem(itemStack);
            if (written.isPresent()) {
                return mergeExtra(resolvers, (CompoundTag) tag, written.get(), ITEM_KEYS, found);
            }
            BackportSettings.count("embedded item unwritable: " + ((CompoundTag) tag).getString("id", "?"));
        } else if (captured instanceof ChunkerBlockIdentifier block) {
            try {
                CompoundTag written = resolvers.writeBlock(block);
                return mergeExtra(resolvers, (CompoundTag) tag, written, BLOCK_KEYS, found);
            } catch (Exception e) {
                BackportSettings.count("embedded block unwritable: " + ((CompoundTag) tag).getString("Name", "?"));
            }
        }

        if (tag instanceof CompoundTag compoundTag) {
            CompoundTag output = new CompoundTag(compoundTag.size());
            for (Map.Entry<String, Tag<?>> entry : compoundTag) {
                output.put(entry.getKey(), rewrite(resolvers, entry.getValue(), found));
            }
            return output;
        } else if (tag instanceof ListTag listTag) {
            ListTag output = new ListTag(listTag.getListType(), listTag.size());
            for (Object child : listTag) {
                output.add(rewrite(resolvers, (Tag<?>) child, found));
            }
            return output;
        }
        return tag.clone();
    }

    private static CompoundTag mergeExtra(JavaResolvers resolvers, CompoundTag original, CompoundTag written, Set<String> own, Map<Tag<?>, Object> found) {
        for (Map.Entry<String, Tag<?>> entry : original) {
            if (!own.contains(entry.getKey()) && !written.contains(entry.getKey())) {
                written.put(entry.getKey(), rewrite(resolvers, entry.getValue(), found));
            }
        }
        return written;
    }
}
