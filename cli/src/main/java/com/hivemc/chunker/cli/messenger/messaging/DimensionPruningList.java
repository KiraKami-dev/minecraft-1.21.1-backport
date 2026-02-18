package com.hivemc.chunker.cli.messenger.messaging;

import com.hivemc.chunker.pruning.PruningConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

/**
 * Class to hold a list of pruning configs ordered by the Dimension ordinal.
 */
public class DimensionPruningList {
    private @Nullable HashMap<String, PruningConfig> configs;

    /**
     * Get the list of configs, if a dimension is absent it should not be pruned.
     *
     * @return the backing list of configs.
     */
    @Nullable
    public HashMap<String, PruningConfig> getConfigs() {
        return configs;
    }

    /**
     * Set the configs to a new list (can be null).
     *
     * @param configs the configs.
     */
    public void setConfigs(@Nullable HashMap<String, PruningConfig> configs) {
        this.configs = configs;
    }
}
