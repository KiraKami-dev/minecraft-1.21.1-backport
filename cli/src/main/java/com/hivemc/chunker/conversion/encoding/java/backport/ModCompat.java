package com.hivemc.chunker.conversion.encoding.java.backport;

import com.hivemc.chunker.nbt.tags.Tag;
import com.hivemc.chunker.nbt.tags.array.IntArrayTag;
import com.hivemc.chunker.nbt.tags.collection.CompoundTag;
import com.hivemc.chunker.nbt.tags.collection.ListTag;
import com.hivemc.chunker.nbt.tags.primitive.StringTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Data format differences between the mod versions of the source (Create 6.0.9 / Waystones / Traveler's Backpack for 1.21.11)
 * and the ones on the 1.21.1 server (Create 6.0.10, Waystones 21.1.46, Traveler's Backpack 10.1.40).
 * Component ids registered by the target jars were read from the classes.
 */
public final class ModCompat {
    private static final Set<String> CREATE = Set.of("banktank_air", "belt_first_shaft", "inferred_from_recipe", "placement_pattern",
            "shaper_brush", "shaper_brush_params", "shaper_placement_options", "shaper_tool", "shaper_block_used", "shaper_swap",
            "shaper_block_data", "filter_items", "filter_items_respect_nbt", "filter_items_blacklist", "attribute_filter_whitelist_mode",
            "attribute_filter_matched_attributes", "clipboard_content", "track_connecting_from", "track_extend_curve",
            "track_targeting_item_selected_pos", "track_targeting_item_selected_direction", "track_targeting_item_bezier",
            "schematic_deployed", "schematic_owner", "schematic_file", "schematic_anchor", "schematic_rotation", "schematic_mirror",
            "schematic_bounds", "schematic_hash", "chromatic_compound_collecting_light", "sand_paper_polishing", "sand_paper_jei",
            "minecart_contraption_data", "linked_controller_items", "toolbox_inventory", "toolbox_uuid", "sequenced_assembly",
            "train_schedule", "symmetry_wand", "symmetry_wand_enable", "symmetry_wand_simulate", "potion_fluid_bottle_type",
            "schematicannon_options", "auto_request_data", "shopping_list", "shopping_list_address", "package_address",
            "package_contents", "package_order_data", "package_order_context", "click_to_link_data", "clipboard_type",
            "clipboard_pages", "clipboard_read_only", "clipboard_copied_values", "clipboard_previously_opened_page");
    private static final Set<String> WAYSTONES = Set.of("waystone", "attunement", "bound_scroll", "twinbound_feather", "twinbound_feather_links");
    private static final Set<String> BACKPACKS = Set.of("storage_slots", "upgrade_slots", "tool_slots", "tier", "fluids", "tab_open",
            "filter_settings", "filter_tags", "upgrade_enabled", "shift_click_to_backpack", "show_tool_slots", "show_more_buttons",
            "is_playing", "cooldown", "starter_upgrades", "render_info", "backpack_container", "upgrades", "tools_container",
            "sleeping_bag_color", "sort_type", "ability_enabled", "is_visible", "slots", "upgrade_tick_interval", "hose_modes",
            "burn_finish_time", "cooking_finish_time", "burn_total_time", "cooking_total_time", "fluid_tanks");

    private ModCompat() {
    }

    /**
     * Convert a modded item component.
     *
     * @return the replacement entries (key to value), empty if the component has no equivalent and is dropped.
     */
    public static Map<String, Tag<?>> component(String key, Tag<?> value) {
        Map<String, Tag<?>> out = new TreeMap<>();
        String namespace = key.substring(0, Math.max(0, key.indexOf(':')));
        String name = key.substring(key.indexOf(':') + 1);
        switch (namespace) {
            case "create" -> {
                if (!CREATE.contains(name)) {
                    BackportSettings.count("mod component dropped: " + key);
                    return out;
                }
                clean(value);
            }
            case "waystones" -> {
                // Renamed, and the name is no longer stored with the id
                if (name.equals("waystone_attunement") || name.equals("waystone_identity")) {
                    Tag<?> id = value instanceof CompoundTag c && c.get("id") != null ? c.get("id") : value;
                    out.put(name.equals("waystone_attunement") ? "waystones:attunement" : "waystones:waystone", id);
                    BackportSettings.count("waystones component converted: " + key);
                    return out;
                }
                if (!WAYSTONES.contains(name)) {
                    BackportSettings.count("mod component dropped: " + key);
                    return out;
                }
            }
            case "travelersbackpack" -> {
                if (!BACKPACKS.contains(name)) {
                    BackportSettings.count("mod component dropped: " + key);
                    return out;
                }
                if (name.equals("starter_upgrades")) value = starterUpgrades(value);
            }
            default -> {
            }
        }
        out.put(key, value);
        return out;
    }

    // [{item: {id, count}, slot}] became a plain list of item stacks
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tag<?> starterUpgrades(Tag<?> value) {
        if (!(value instanceof ListTag<?, ?> list)) return value;
        TreeMap<Integer, Tag<?>> bySlot = new TreeMap<>();
        List<Tag<?>> plain = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof CompoundTag entry && entry.get("item") instanceof CompoundTag item) {
                if (item.contains("id")) bySlot.put(entry.getInt("slot", bySlot.size()), item);
            } else if (o instanceof CompoundTag entry && entry.contains("id")) {
                plain.add(entry);
            }
        }
        List<Tag> items = new ArrayList<>(plain);
        items.addAll(bySlot.values());
        BackportSettings.count("backpack starter upgrades converted");
        return new ListTag(items);
    }

    /**
     * Fixes valid anywhere in a tag (item components, fluid stacks in block entities / entities): fluid components Create 6.0.10
     * doesn't have, and tag references (#ns:tag) which are plain ids in the attribute filters.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void clean(Tag<?> tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.remove("create:fluid_max_capacity") != null) BackportSettings.count("create:fluid_max_capacity removed");
            if (compound.get("type") instanceof StringTag type && type.getValue().equals("create:in_tag")
                    && compound.get("value") instanceof StringTag value && value.getValue().startsWith("#")) {
                compound.put("value", value.getValue().substring(1));
                BackportSettings.count("create:in_tag '#' removed");
            }
            if (compound.get("fluidVariant") instanceof CompoundTag variant && variant.get("fluid") instanceof StringTag fluid) {
                // Fabric fluid stack {fluidVariant: {fluid}, amount: long} to the NeoForge one {id, amount: int}
                compound.remove("fluidVariant");
                compound.put("id", fluid.getValue());
                if (compound.get("amount") instanceof com.hivemc.chunker.nbt.tags.primitive.LongTag amount) {
                    compound.put("amount", (int) Math.min(Integer.MAX_VALUE, amount.getValue()));
                }
                if (variant.get("components") != null) compound.put("components", variant.get("components"));
                BackportSettings.count("fabric fluid stacks converted");
            }
            for (Tag<?> child : new ArrayList<>(compound.getValue().values())) clean(child);
        } else if (tag instanceof ListTag<?, ?> list) {
            for (Object child : list) clean((Tag<?>) child);
        }
    }

    /**
     * Block entity ids and formats of Create that changed.
     */
    public static void blockEntity(CompoundTag tag) {
        String id = tag.getString("id", "");
        if (id.equals("create:bracketed_kinetic")) {
            // Shafts and cogwheels use the plain kinetic block entity in 6.0.10
            tag.put("id", "create:simple_kinetic");
            BackportSettings.count("create:bracketed_kinetic -> simple_kinetic");
        } else if (id.equals("create:track") && tag.get("Connections") instanceof ListTag<?, ?> connections) {
            for (Object o : connections) {
                if (o instanceof CompoundTag connection) bezier(connection);
            }
            BackportSettings.count("create:track connections converted");
        }
        clean(tag);
    }

    /**
     * A Create BezierConnection compound (track block entity connection, or the edge of a train graph): the Couples of
     * positions / vectors / smoothing have compound elements in 6.0.10.
     */
    public static void bezier(CompoundTag connection) {
        wrap(connection, "Positions", "Pos");
        for (String key : new String[]{"Starts", "Axes", "Normals"}) wrap(connection, key, "V");
        wrap(connection, "Smoothing", "V");
    }

    // The elements of a Couple are compounds with the value under a key in 6.0.10
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wrap(CompoundTag connection, String key, String inner) {
        if (!(connection.get(key) instanceof ListTag<?, ?> list)) return;
        List<Tag> wrapped = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof CompoundTag) { // already converted
                wrapped.add((Tag) o);
                continue;
            }
            CompoundTag compound = new CompoundTag();
            compound.put(inner, (Tag<?>) o);
            wrapped.add(compound);
        }
        connection.put(key, new ListTag(wrapped));
    }
}
