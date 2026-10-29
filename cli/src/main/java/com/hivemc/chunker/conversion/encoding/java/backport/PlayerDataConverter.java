package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.conversion.encoding.java.JavaDataVersion;
import com.hivemc.chunker.nbt.TagType;
import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.ByteTag;
import com.hivemc.chunker.nbt.tags.primitive.DoubleTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Chunker doesn't convert the per player files of a server world, this does it for the backport:
 * playerdata/*.dat is downgraded, advancements/ and stats/ are copied as they are.
 */
public final class PlayerDataConverter {
    private static final String[] ARMOR = {"feet", "legs", "chest", "head"};
    private static final byte[] ARMOR_SLOTS = {100, 101, 102, 103};

    private PlayerDataConverter() {
    }

    public static void convertWorld(File input, File output, JavaDataVersion target) throws IOException {
        File playerData = new File(input, "playerdata");
        File[] files = playerData.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files != null) {
            File out = new File(output, "playerdata");
            Files.createDirectories(out.toPath());
            for (File file : files) {
                CompoundTag root = Tag.readGZipJavaNBT(file);
                JavaDataVersion source = JavaDataVersion.getNearestVersion(root.getInt("DataVersion", target.getDataVersion()));
                convertPlayer(root, source, target);
                Tag.writeGZipJavaNBT(new File(out, file.getName()), root);
                BackportSettings.count("playerdata files converted");
            }
        }
        convertBackpacks(input, output, target);
        for (String directory : new String[]{"advancements", "stats"}) {
            File[] copies = new File(input, directory).listFiles(File::isFile);
            if (copies == null) continue;
            File out = new File(output, directory);
            Files.createDirectories(out.toPath());
            for (File file : copies) {
                Files.copy(file.toPath(), new File(out, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                BackportSettings.count(directory + " files copied");
            }
        }
    }

    /**
     * Traveler's Backpack keeps a copy of every backpack that is dropped / destroyed in backpacks/<player uuid>/*.dat (an item stack
     * written as plain NBT); the 1.21.1 build reads the same folder (BackpackManager, "/tb restore"). The stacks are downgraded.
     */
    private static void convertBackpacks(File input, File output, JavaDataVersion target) throws IOException {
        File[] players = new File(input, "backpacks").listFiles(File::isDirectory);
        if (players == null) return;
        JavaDataVersion source = target;
        File level = new File(input, "level.dat");
        if (level.isFile()) {
            CompoundTag data = Tag.readGZipJavaNBT(level); // the "Data" tag
            if (data != null) source = JavaDataVersion.getNearestVersion(data.getInt("DataVersion", target.getDataVersion()));
        }
        for (File player : players) {
            File[] files = player.listFiles((dir, name) -> name.endsWith(".dat"));
            if (files == null) continue;
            File out = new File(output, "backpacks/" + player.getName());
            Files.createDirectories(out.toPath());
            for (File file : files) {
                CompoundTag stack = Tag.readPossibleGZipJavaNBT(file);
                CompoundTag converted = ItemDowngrade.item(stack, source, target);
                if (converted == null) {
                    BackportSettings.count("backpack backups dropped (no equivalent item)");
                    continue;
                }
                Tag.writeUncompressedJavaNBT(new File(out, file.getName()), converted);
                BackportSettings.count("backpack backups converted");
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void convertPlayer(CompoundTag player, JavaDataVersion source, JavaDataVersion target) {
        // Items first: inventory, ender chest, equipment and anything a mod stored on the player
        ItemDowngrade.walk(player, source, target);

        if (EntityDowngrade.applies(source, target)) {
            // Equipment went back into the inventory (armor 100-103, offhand -106)
            CompoundTag equipment = player.getCompound("equipment");
            if (equipment != null) {
                ListTag inventory = player.getList("Inventory", CompoundTag.class, null);
                if (inventory == null) {
                    inventory = new ListTag<>(TagType.COMPOUND);
                    player.put("Inventory", inventory);
                }
                for (int i = 0; i < ARMOR.length; i++) addSlot(inventory, equipment.getCompound(ARMOR[i]), ARMOR_SLOTS[i]);
                addSlot(inventory, equipment.getCompound("offhand"), (byte) -106);
                player.remove("equipment");
            }

            // Attribute ids and modifiers
            ListTag<CompoundTag, Map<String, Tag<?>>> attributes = player.getList("attributes", CompoundTag.class, null);
            if (attributes != null) {
                for (int i = attributes.size() - 1; i >= 0; i--) {
                    CompoundTag attribute = attributes.get(i);
                    String renamed = ItemDowngrade.oldAttribute(attribute.getString("id", ""));
                    if (renamed == null) {
                        BackportSettings.count("player attribute dropped: " + attribute.getString("id", ""));
                        attributes.getValue().remove(i);
                        continue;
                    }
                    attribute.put("id", renamed);
                }
            }

            if (player.get("fall_distance") instanceof DoubleTag fall) {
                player.put("FallDistance", (float) (double) fall.getValue());
                player.remove("fall_distance");
            }
            int[] sleeping = player.getIntArray("sleeping_pos", null);
            if (sleeping != null && sleeping.length == 3) {
                player.put("SleepingX", sleeping[0]);
                player.put("SleepingY", sleeping[1]);
                player.put("SleepingZ", sleeping[2]);
                player.remove("sleeping_pos");
            }

            // The respawn point (1.21.9+) was SpawnX / SpawnY / SpawnZ ...
            CompoundTag respawn = player.getCompound("respawn");
            if (respawn != null) {
                int[] pos = respawn.getIntArray("pos", null);
                if (pos != null && pos.length == 3) {
                    player.put("SpawnX", pos[0]);
                    player.put("SpawnY", pos[1]);
                    player.put("SpawnZ", pos[2]);
                    player.put("SpawnAngle", respawn.getFloat("angle", 0F));
                    player.put("SpawnForced", respawn.getByte("forced", (byte) 0));
                    if (respawn.contains("dimension")) player.put("SpawnDimension", respawn.getString("dimension", "minecraft:overworld"));
                }
                player.remove("respawn");
            }

            for (String shoulder : new String[]{"ShoulderEntityLeft", "ShoulderEntityRight"}) {
                CompoundTag entity = player.getCompound(shoulder);
                if (entity != null && entity.size() > 0) EntityDowngrade.entity(entity, source, target);
            }
            CompoundTag vehicle = player.getCompound("RootVehicle");
            if (vehicle != null && vehicle.getCompound("Entity") != null) {
                EntityDowngrade.entity(vehicle.getCompound("Entity"), source, target);
            }
        }
        // Balm (Waystones: activated waystones, sorting) keeps its data in the persistent data of the player
        CompoundTag balm = player.getCompound("BalmData");
        if (balm != null && !player.contains("NeoForgeData")) {
            player.remove("BalmData");
            CompoundTag persisted = new CompoundTag();
            persisted.put("BalmData", balm);
            CompoundTag neoForge = new CompoundTag();
            neoForge.put("PlayerPersisted", persisted);
            player.put("NeoForgeData", neoForge);
            BackportSettings.count("balm player data moved to NeoForgeData");
        }
        player.put("DataVersion", target.getDataVersion());
        BackportSettings.count("players converted");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addSlot(ListTag inventory, CompoundTag item, byte slot) {
        if (item == null || item.size() == 0) return;
        item.put("Slot", new ByteTag(slot));
        inventory.add(item);
    }
}
