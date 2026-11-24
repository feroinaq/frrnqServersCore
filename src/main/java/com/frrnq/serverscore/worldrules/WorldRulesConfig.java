package com.frrnq.serverscore.worldrules;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Shared server settings; command changes are saved before becoming active. */
public final class WorldRulesConfig {
    private static final String CATEGORY = "worldrules";
    private final Configuration config;
    private final File file;
    private boolean buildProtectionEnabled;
    private int minBuildY;
    private boolean pvpProtectionEnabled;
    private int maxPvpY;
    private int dimensionId;

    public WorldRulesConfig(File file) {
        this.file = file;
        config = new Configuration(file);
    }

    public void load() {
        config.load();
        buildProtectionEnabled = config.getBoolean("buildProtectionEnabled", CATEGORY, true, "Enable build protection.");
        minBuildY = config.getInt("minBuildY", CATEGORY, 50, 0, 255, "Minimum placement Y; vanilla ladders are exempt.");
        pvpProtectionEnabled = config.getBoolean("pvpProtectionEnabled", CATEGORY, true, "Enable PvP protection.");
        maxPvpY = config.getInt("maxPvpY", CATEGORY, 120, 0, 255, "PvP is allowed at or below this Y.");
        dimensionId = config.getInt("dimensionId", CATEGORY, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, "Dimension where rules apply (0 = Overworld).");
        if (config.hasChanged()) {
            config.save();
        }
    }

    public boolean isBuildProtectionEnabled() { return buildProtectionEnabled; }
    public int getMinBuildY() { return minBuildY; }
    public boolean isPvpProtectionEnabled() { return pvpProtectionEnabled; }
    public int getMaxPvpY() { return maxPvpY; }
    public int getDimensionId() { return dimensionId; }

    public void setMinBuildY(int value) {
        saveHeight("minBuildY", value);
        minBuildY = value;
    }

    public void setMaxPvpY(int value) {
        saveHeight("maxPvpY", value);
        maxPvpY = value;
    }

    private void saveHeight(String key, int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Y must be between 0 and 255");
        }
        int previous = config.get(CATEGORY, key, value).getInt();
        config.get(CATEGORY, key, value).set(value);
        config.save();
        // Forge logs IO errors instead of throwing them: verify the value on disk.
        Configuration persisted = new Configuration(file);
        persisted.load();
        if (!persisted.hasKey(CATEGORY, key) || persisted.get(CATEGORY, key, -1).getInt() != value) {
            config.get(CATEGORY, key, previous).set(previous);
            throw new IllegalStateException("Could not save WorldRules configuration");
        }
    }
}