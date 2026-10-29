package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.intermediate.column.ChunkerColumn;
import com.hivemc.chunker.conversion.intermediate.column.blockentity.BlockEntity;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.ChunkerBlockIdentifier;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.type.block.states.BlockState;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.type.block.states.BlockStateValue;
import com.hivemc.chunker.conversion.intermediate.column.chunk.identifier.type.block.ChunkerCustomBlockType;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;

import java.util.*;

/**
 * Universal Graves (a Fabric mod with no 1.21.1 NeoForge build) become plain chests holding the grave's items.
 * <p>
 * The block entity is read as a normal chest (so its items go through the item conversion), the block is replaced
 * with {@code minecraft:chest} just before the column is written (the blocks mapping can map the grave block to anything,
 * this runs afterwards and does not depend on it), and items which don't fit in 27 slots go to a second chest on a free
 * adjacent block.
 */
public final class GraveMapping {
    public static final String GRAVE_ID = "universal_graves:grave";
    private static final int SLOTS = 27;
    private static final String[] FACING = {"south", "west", "north", "east"};

    private GraveMapping() {
    }

    /**
     * The chest compounds for a grave block entity.
     *
     * @param main     the chest at the grave position.
     * @param overflow the chest for items which did not fit or null.
     */
    public record Chests(CompoundTag main, CompoundTag overflow) {
    }

    /**
     * Convert a grave block entity (as stored in the world) to chest NBT using the grave's saved data.
     *
     * @return the chests or null if the grave is not known (it is then left as it was).
     */
    public static Chests convert(CompoundTag input) {
        if (!GRAVE_ID.equals(input.getString("id", ""))) return null;
        long graveId = input.getLong("GraveId", -1L);
        Optional<CompoundTag> grave = BackportSettings.getGrave(graveId);
        if (grave.isEmpty()) {
            BackportSettings.count("grave data missing");
            System.err.println("[backport] grave " + graveId + " at " + input.getInt("x", 0) + "," + input.getInt("y", 0) + "," + input.getInt("z", 0) + " has no entry in universal-graves.dat, left as it is");
            return null;
        }

        List<CompoundTag> items = new ArrayList<>();
        ListTag<CompoundTag, Map<String, Tag<?>>> list = grave.get().getList("Items", CompoundTag.class, null);
        if (list != null) {
            for (CompoundTag entry : list) {
                CompoundTag item = entry.getCompound("Item");
                if (item == null || item.size() == 0 || !item.contains("id")) continue;
                items.add(item);
            }
        }

        int x = input.getInt("x", 0);
        int y = input.getInt("y", 0);
        int z = input.getInt("z", 0);
        CompoundTag main = chest(x, y, z, items.subList(0, Math.min(SLOTS, items.size())));
        CompoundTag overflow = null;
        if (items.size() > SLOTS) {
            overflow = chest(x, y, z, items.subList(SLOTS, Math.min(items.size(), SLOTS * 2)));
            BackportSettings.count("graves with overflow chest");
            if (items.size() > SLOTS * 2) {
                BackportSettings.count("grave items lost (more than 54)", items.size() - SLOTS * 2);
            }
        }
        int xp = grave.get().getInt("XP", 0);
        if (xp > 0) BackportSettings.count("grave experience lost", xp);
        BackportSettings.count("graves converted");
        System.out.println("[backport] grave " + graveId + " at " + x + "," + y + "," + z + " -> chest with " + items.size() + " stacks" + (overflow != null ? " (overflow chest)" : ""));
        return new Chests(main, overflow);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CompoundTag chest(int x, int y, int z, List<CompoundTag> items) {
        CompoundTag chest = new CompoundTag(6);
        chest.put("id", "minecraft:chest");
        chest.put("x", x);
        chest.put("y", y);
        chest.put("z", z);
        ListTag list = new ListTag<>(TagType.COMPOUND, items.size());
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.get(i).clone();
            item.put("Slot", new ByteTag((byte) i));
            list.add(item);
        }
        chest.put("Items", list);
        return chest;
    }

    /**
     * Replace the grave blocks of a column with chests and place the overflow chests.
     * Called before the column is written.
     */
    public static void placeBeforeWrite(ChunkerColumn column) {
        List<BlockEntity> added = null;
        for (BlockEntity blockEntity : column.getBlockEntities()) {
            if (!blockEntity.isGrave()) continue;
            int x = blockEntity.getX();
            int y = blockEntity.getY();
            int z = blockEntity.getZ();
            ChunkerBlockIdentifier current = column.getBlock(x, y, z);
            String facing = "north";
            String waterlogged = "false";
            for (Map.Entry<BlockState<?>, BlockStateValue> state : current.getPresentStates().entrySet()) {
                Object value = state.getValue() instanceof ChunkerCustomBlockType.CustomBlockStateValue<?> custom ? custom.getStateValue() : state.getValue();
                if (state.getKey().getName().equals("rotation")) {
                    try {
                        facing = FACING[(Integer.parseInt(String.valueOf(value)) / 4) & 3];
                    } catch (NumberFormatException ignored) {
                    }
                } else if (state.getKey().getName().equals("waterlogged")) {
                    waterlogged = String.valueOf(value).toLowerCase(Locale.ROOT).contains("true") ? "true" : "false";
                }
            }
            ChunkerBlockIdentifier chest = chest(facing, waterlogged);
            column.setBlock(x, y, z, chest);

            BlockEntity overflow = blockEntity.getGraveOverflow();
            if (overflow == null) continue;
            blockEntity.setGraveOverflow(null);
            int[][] directions = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
            boolean placed = false;
            for (int[] d : directions) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                // Stay inside this column
                if ((nx >> 4) != (x >> 4) || (nz >> 4) != (z >> 4)) continue;
                if (!column.getBlock(nx, ny, nz).isAir()) continue;
                column.setBlock(nx, ny, nz, chest);
                overflow.setX(nx);
                overflow.setY(ny);
                overflow.setZ(nz);
                if (added == null) added = new ArrayList<>();
                added.add(overflow);
                placed = true;
                System.out.println("[backport] grave at " + x + "," + y + "," + z + ": overflow chest at " + nx + "," + ny + "," + nz);
                break;
            }
            if (!placed) {
                BackportSettings.count("grave overflow chests without free space");
                System.err.println("[backport] grave at " + x + "," + y + "," + z + ": no free adjacent block, overflow items lost");
            }
        }
        if (added != null) column.getBlockEntities().addAll(added);
    }

    private static ChunkerBlockIdentifier chest(String facing, String waterlogged) {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("facing", facing);
        states.put("type", "single");
        states.put("waterlogged", waterlogged);
        return ChunkerBlockIdentifier.custom("minecraft:chest", states);
    }
}
