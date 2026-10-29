package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.array.IntArrayTag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.DoubleTag;
import com.hivemc.chunker.nbt.tags.primitive.FloatTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Global mod saved data (world/data/*.dat) whose format changed between the mods of the source (1.21.11) and the 1.21.1 ones:
 * Waystones (waystones.dat, name generator), the Create train graph (create_tracks.dat: track graphs, signals, stations, trains
 * with their carriage contraptions) and the per player Waystones data (see PlayerDataConverter).
 * The formats were compared against the Waystones 21.1.46 and Create 6.0.10 classes.
 */
public final class ModWorldData {
    private ModWorldData() {
    }

    public static void convertWorld(File input, File output, JavaDataVersion target) throws IOException {
        File data = new File(input, "data");
        if (!data.isDirectory()) return;
        File out = new File(output, "data");
        Files.createDirectories(out.toPath());
        JavaDataVersion source = sourceVersion(input, target);

        File waystones = new File(data, "waystones.dat");
        if (waystones.isFile()) {
            // (readGZipJavaNBT returns the content of the "data" tag)
            CompoundTag content = Tag.readGZipJavaNBT(waystones);
            waystones(content);
            write(new File(out, "waystones.dat"), content, target);
            BackportSettings.count("waystones.dat converted");
        }
        File names = new File(data, "waystones_name_generator.dat");
        if (names.isFile()) {
            // Only renamed
            write(new File(out, "waystones_NameGenerator.dat"), Tag.readGZipJavaNBT(names), target);
            BackportSettings.count("waystones name generator converted");
        }
        File tracks = new File(data, "create_tracks.dat");
        if (tracks.isFile()) {
            CompoundTag content = Tag.readGZipJavaNBT(tracks);
            railways(content, source, target);
            write(new File(out, "create_tracks.dat"), content, target);
            BackportSettings.count("create_tracks.dat converted");
        }
    }

    static JavaDataVersion sourceVersion(File input, JavaDataVersion target) throws IOException {
        File level = new File(input, "level.dat");
        if (level.isFile()) {
            CompoundTag data = Tag.readGZipJavaNBT(level); // the "Data" tag
            if (data != null) return JavaDataVersion.getNearestVersion(data.getInt("DataVersion", target.getDataVersion()));
        }
        return target;
    }

    private static void write(File file, CompoundTag content, JavaDataVersion target) throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("data", content);
        root.put("DataVersion", target.getDataVersion());
        Tag.writeGZipJavaNBT(file, root);
    }

    // ------------------------------------------------------------------ Waystones

    /**
     * Name is a JSON text component string, the enums are stored by (upper case) name.
     */
    public static void waystones(CompoundTag data) {
        if (data == null) return;
        ListTag<CompoundTag, Map<String, Tag<?>>> list = data.getList("Waystones", CompoundTag.class, null);
        if (list == null) return;
        for (CompoundTag waystone : list) {
            EntityDowngrade.textToJson(waystone, "NameV2");
            upper(waystone, "Origin");
            upper(waystone, "Visibility");
        }
    }

    // ------------------------------------------------------------------ Create railways

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void railways(CompoundTag data, JavaDataVersion source, JavaDataVersion target) {
        if (data == null) return;
        // Fluid stacks / filters / items anywhere in the data
        ModCompat.clean(data);

        // The dimension palette is a list of {Id}
        if (data.get("DimensionPalette") instanceof ListTag<?, ?> palette) {
            List<Tag> dims = new ArrayList<>();
            for (Object o : palette) {
                if (o instanceof StringTag s) {
                    CompoundTag c = new CompoundTag();
                    c.put("Id", s.getValue());
                    dims.add(c);
                } else {
                    dims.add((Tag) o);
                }
            }
            data.put("DimensionPalette", compounds(dims));
        }

        ListTag<CompoundTag, Map<String, Tag<?>>> graphs = data.getList("RailGraphs", CompoundTag.class, null);
        if (graphs != null) {
            for (CompoundTag graph : graphs) {
                ListTag<CompoundTag, Map<String, Tag<?>>> nodes = graph.getList("Nodes", CompoundTag.class, null);
                if (nodes != null) {
                    for (CompoundTag node : nodes) {
                        ListTag<CompoundTag, Map<String, Tag<?>>> connections = node.getList("Connections", CompoundTag.class, null);
                        if (connections == null) continue;
                        for (CompoundTag connection : connections) {
                            CompoundTag edge = connection.getCompound("EdgeData");
                            if (edge != null) edge(edge);
                        }
                    }
                }
                CompoundTag points = graph.getCompound("Points");
                if (points != null) {
                    for (Map.Entry<String, Tag<?>> type : points) {
                        if (!(type.getValue() instanceof ListTag<?, ?> list)) continue;
                        for (Object o : list) {
                            if (o instanceof CompoundTag point) edgePoint(point, source, target);
                        }
                    }
                }
            }
        }

        // Signal edge groups: Id is a UUID, Color an enum, Connected a list of {Key, Value}
        ListTag<CompoundTag, Map<String, Tag<?>>> groups = data.getList("SignalBlocks", CompoundTag.class, null);
        if (groups != null) {
            for (CompoundTag group : groups) {
                uuid(group, "Id");
                upper(group, "Color");
                if (group.get("Connected") instanceof CompoundTag connected) {
                    List<Tag> entries = new ArrayList<>();
                    if (connected.get("Keys") instanceof ListTag<?, ?> keys && connected.get("Values") instanceof ListTag<?, ?> values) {
                        for (int i = 0; i < keys.size() && i < values.size(); i++) {
                            CompoundTag entry = new CompoundTag();
                            entry.put("Key", uuidTag((Tag<?>) keys.get(i)));
                            entry.put("Value", uuidTag((Tag<?>) values.get(i)));
                            entries.add(entry);
                        }
                    } else {
                        for (Map.Entry<String, Tag<?>> e : connected) {
                            CompoundTag entry = new CompoundTag();
                            entry.put("Key", uuidTag(new StringTag(e.getKey())));
                            entry.put("Value", uuidTag(e.getValue()));
                            entries.add(entry);
                        }
                    }
                    group.put("Connected", compounds(entries));
                }
            }
        }

        ListTag<CompoundTag, Map<String, Tag<?>>> trains = data.getList("Trains", CompoundTag.class, null);
        if (trains != null) {
            for (CompoundTag train : trains) train(train, source, target);
        }
        BackportSettings.count("create trains converted", trains == null ? 0 : trains.size());
        BackportSettings.count("create track graphs converted", graphs == null ? 0 : graphs.size());
    }

    // The bezier connection is stored in the edge compound itself in 6.0.10
    private static void edge(CompoundTag edge) {
        if (edge.get("BezierConnection") instanceof CompoundTag bezier) {
            edge.remove("BezierConnection");
            for (Map.Entry<String, Tag<?>> entry : bezier) {
                if (!edge.contains(entry.getKey())) edge.put(entry.getKey(), entry.getValue());
            }
        }
        ModCompat.bezier(edge);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void edgePoint(CompoundTag point, JavaDataVersion source, JavaDataVersion target) {
        // Station: connected package ports are a list of {Pos, Address, Primed, OfflineBuffer}
        if (point.get("Ports") instanceof CompoundTag ports) {
            List<Tag> entries = new ArrayList<>();
            if (ports.get("Keys") instanceof ListTag<?, ?> keys && ports.get("Values") instanceof ListTag<?, ?> values) {
                for (int i = 0; i < keys.size() && i < values.size(); i++) {
                    CompoundTag entry = values.get(i) instanceof CompoundTag c ? c : new CompoundTag();
                    entry.put("Pos", (Tag<?>) keys.get(i));
                    entries.add(entry);
                }
            }
            point.put("Ports", compounds(entries));
        }
        // Signals
        upper(point, "Type1");
        upper(point, "Type2");
        ItemDowngrade.walk(point, source, target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void train(CompoundTag train, JavaDataVersion source, JavaDataVersion target) {
        EntityDowngrade.textToJson(train, "Name");
        CompoundTag runtime = train.getCompound("Runtime");
        if (runtime != null) {
            upper(runtime, "State");
            ItemDowngrade.walk(runtime, source, target);
        }
        ListTag<CompoundTag, Map<String, Tag<?>>> carriages = train.getList("Carriages", CompoundTag.class, null);
        if (carriages == null) return;
        for (CompoundTag carriage : carriages) {
            ListTag<CompoundTag, Map<String, Tag<?>>> positioning = carriage.getList("EntityPositioning", CompoundTag.class, null);
            if (positioning != null) {
                for (CompoundTag p : positioning) {
                    wrapList(p, "RotationAnchors", "V");
                    ItemDowngrade.walk(p, source, target);
                }
            }
            CompoundTag entity = carriage.getCompound("Entity");
            if (entity != null) {
                // Items first (the contraption's block entities, actors, storage, passengers); in the world Chunker converts the
                // items of an entity already, that's why entityContraption leaves them alone
                ItemDowngrade.walk(entity, source, target);
                EntityDowngrade.entity(entity, source, target); // includes the contraption (entityContraption)
            }
            CompoundTag passengers = carriage.getCompound("Passengers");
            if (passengers != null) {
                for (Map.Entry<String, Tag<?>> seat : passengers) {
                    if (seat.getValue() instanceof CompoundTag passenger) {
                        ItemDowngrade.walk(passenger, source, target);
                        EntityDowngrade.entity(passenger, source, target);
                    }
                }
            }
        }
    }

    /**
     * A Create contraption entity (carriage, stationary / controlled / oriented contraption): called for every entity by
     * EntityDowngrade.entity, so the ones in the world and the ones in the train data are converted the same way, once.
     */
    public static void entityContraption(CompoundTag entity, JavaDataVersion source, JavaDataVersion target) {
        if (entity.get("Contraption") instanceof CompoundTag contraption) {
            upper(entity, "InitialOrientation");
            contraption(contraption, source, target);
            BackportSettings.count("create contraption entities converted");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void contraption(CompoundTag contraption, JavaDataVersion source, JavaDataVersion target) {
        upper(contraption, "AssemblyDirection");

        // Block entity data of the contraption blocks
        CompoundTag blocks = contraption.getCompound("Blocks");
        if (blocks != null) {
            ListTag<CompoundTag, Map<String, Tag<?>>> list = blocks.getList("BlockList", CompoundTag.class, null);
            if (list != null) {
                for (CompoundTag block : list) {
                    for (String key : new String[]{"Data", "UpdateTag"}) {
                        if (block.get(key) instanceof CompoundTag be) {
                            ModCompat.blockEntity(be);
                            EntityDowngrade.textToJson(be, "CustomName");
                        }
                    }
                }
            }
        }

        // Blocks positions are compounds with the block pos under "Pos"
        wrapList(contraption, "Seats", "Pos");
        wrapList(contraption, "Interactors", "Pos");
        if (contraption.get("CapturedMultiblocks") instanceof ListTag<?, ?> multiblocks) {
            for (Object o : multiblocks) {
                if (o instanceof CompoundTag multiblock) wrapList(multiblock, "Parts", "Pos");
            }
        }

        // Super glue boxes are {From, To}
        if (contraption.get("Superglue") instanceof ListTag<?, ?> glue) {
            List<Tag> boxes = new ArrayList<>();
            for (Object o : glue) {
                if (o instanceof ListTag<?, ?> box && box.size() == 6) {
                    CompoundTag c = new CompoundTag();
                    c.put("From", new ListTag(List.of(box.get(0), box.get(1), box.get(2))));
                    c.put("To", new ListTag(List.of(box.get(3), box.get(4), box.get(5))));
                    boxes.add(c);
                } else {
                    boxes.add((Tag) o);
                }
            }
            contraption.put("Superglue", compounds(boxes));
        }

        // The bounds are a list of 6 floats
        if (contraption.get("BoundsFront") instanceof ListTag<?, ?> bounds && bounds.size() > 0 && bounds.get(0) instanceof DoubleTag) {
            List<Tag> floats = new ArrayList<>();
            for (Object o : bounds) floats.add(new FloatTag((float) (double) ((DoubleTag) o).getValue()));
            contraption.put("BoundsFront", new ListTag(floats));
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Each element of the list at key becomes a compound {inner: element}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wrapList(CompoundTag parent, String key, String inner) {
        if (!(parent.get(key) instanceof ListTag<?, ?> list)) return;
        List<Tag> wrapped = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof CompoundTag) {
                wrapped.add((Tag) o);
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.put(inner, (Tag<?>) o);
            wrapped.add(c);
        }
        parent.put(key, compounds(wrapped));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ListTag compounds(List<Tag> list) {
        return list.isEmpty() ? new ListTag<>(TagType.COMPOUND) : new ListTag(list);
    }

    private static void upper(CompoundTag tag, String key) {
        if (tag.get(key) instanceof StringTag s) tag.put(key, s.getValue().toUpperCase(Locale.ROOT));
    }

    private static void uuid(CompoundTag tag, String key) {
        if (tag.get(key) != null) tag.put(key, uuidTag(tag.get(key)));
    }

    private static Tag<?> uuidTag(Tag<?> tag) {
        if (tag instanceof StringTag s) {
            UUID uuid = UUID.fromString(s.getValue());
            long msb = uuid.getMostSignificantBits(), lsb = uuid.getLeastSignificantBits();
            return new IntArrayTag(new int[]{(int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb});
        }
        return tag;
    }
}
