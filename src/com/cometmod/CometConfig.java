package com.cometmod;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.cometmod.config.BossEntry;
import com.cometmod.config.DefaultThemes;
import com.cometmod.config.MobEntry;
import com.cometmod.config.ThemeConfig;
import com.cometmod.config.ThemeConfigParser;
import com.cometmod.config.ThemeConfigWriter;
import com.cometmod.config.TierSettings;
import com.cometmod.config.TierRewards;
import com.cometmod.config.ZoneSpawnChances;

/**
 * Configuration manager for Comet Mod settings.
 * Handles spawn settings, themes, and tier configurations.
 * Config file is the single source of truth - no fallback to hardcoded after
 * first run.
 */
public class CometConfig {

    private static final Logger LOGGER = Logger.getLogger("CometConfig");
    private static final String CONFIG_FILE_NAME = "comet_config.json";

    // Singleton instance for global access
    private static CometConfig instance;

    // Spawn settings (existing)
    public int minDelaySeconds = 120;
    public int maxDelaySeconds = 300;
    public double spawnChance = 0.4;
    public double despawnTimeMinutes = 30.0;
    public int minSpawnDistance = 30;
    public int maxSpawnDistance = 50;

    // Global comets setting - if true, any player can trigger any comet (not just the owner)
    public boolean globalComets = false;

    // Theme configurations (new)
    private Map<String, ThemeConfig> themes = new LinkedHashMap<>();
    private List<ThemeConfig> themeList = new ArrayList<>(); // Ordered list for random selection

    // Tier settings (new)
    private Map<Integer, TierSettings> tierSettings = new LinkedHashMap<>();

    // Reward settings (new)
    private Map<Integer, TierRewards> rewardSettings = new LinkedHashMap<>();

    // Zone spawn chances (configurable tier probabilities per zone)
    private Map<String, ZoneSpawnChances> zoneSpawnChances = new LinkedHashMap<>();

    // Bench recipes (new)

    // Track if config was loaded successfully
    private boolean themesLoaded = false;

    /**
     * Get the singleton instance (loaded config)
     */
    public static CometConfig getInstance() {
        return instance;
    }

    /**
     * Get config file location
     */
    private static File getConfigFile() {
        CometModPlugin plugin = CometModPlugin.getInstance();
        if (plugin != null) {
            try {
                java.nio.file.Path pluginFile = plugin.getFile();
                if (pluginFile != null) {
                    java.nio.file.Path pluginDir = pluginFile.getParent();
                    if (pluginDir != null) {
                        String dirName = pluginDir.getFileName().toString();
                        java.nio.file.Path modFolder;

                        if ("Mods".equals(dirName) || "mods".equals(dirName)) {
                            modFolder = pluginDir.resolve("CometMod");
                        } else {
                            modFolder = pluginDir;
                        }

                        File modFolderFile = modFolder.toFile();
                        if (!modFolderFile.exists()) {
                            modFolderFile.mkdirs();
                        }

                        File configFile = modFolder.resolve(CONFIG_FILE_NAME).toFile();
                        LOGGER.info("Using plugin directory for config: " + configFile.getAbsolutePath());
                        return configFile;
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Could not get plugin directory, using fallback: " + e.getMessage());
            }
        }

        // Fallback paths
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            File modFolder = new File(appData + File.separator + "Hytale" + File.separator +
                    "UserData" + File.separator + "Mods" + File.separator + "CometMod");
            if (!modFolder.exists()) {
                modFolder.mkdirs();
            }
            return new File(modFolder, CONFIG_FILE_NAME);
        }

        File currentDir = new File(System.getProperty("user.dir"));
        File modFolder1 = new File(currentDir, "Mods" + File.separator + "CometMod");
        if (modFolder1.exists() || modFolder1.getParentFile().exists()) {
            modFolder1.mkdirs();
            return new File(modFolder1, CONFIG_FILE_NAME);
        }

        File fallbackModFolder = new File(currentDir, "CometMod");
        fallbackModFolder.mkdirs();
        LOGGER.warning("Could not find mod directory, saving config to: " + fallbackModFolder.getAbsolutePath());
        return new File(fallbackModFolder, CONFIG_FILE_NAME);
    }

    /**
     * Load configuration from file, or create with defaults if file doesn't exist.
     * Config file is the single source of truth after creation.
     */
    public static CometConfig load() {
        File configFile = getConfigFile();
        CometConfig config = new CometConfig();

        if (configFile.exists() && configFile.isFile()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                config = parseJson(content);
                LOGGER.info("Loaded Comet Mod configuration from: " + configFile.getAbsolutePath());

                // Log spawn settings
                LOGGER.info("  Spawn Settings:");
                LOGGER.info("    minDelaySeconds: " + config.minDelaySeconds);
                LOGGER.info("    maxDelaySeconds: " + config.maxDelaySeconds);
                LOGGER.info("    spawnChance: " + config.spawnChance);
                LOGGER.info("    despawnTimeMinutes: " + config.despawnTimeMinutes);
                LOGGER.info("    minSpawnDistance: " + config.minSpawnDistance);
                LOGGER.info("    maxSpawnDistance: " + config.maxSpawnDistance);

                // Log themes
                LOGGER.info("  Themes loaded: " + config.themes.size());
                for (ThemeConfig theme : config.themes.values()) {
                    LOGGER.info("    - " + theme.getId() + " (" + theme.getDisplayName() + ") - Tiers: "
                            + theme.getTiers());
                }

                // Warn if no themes
                if (config.themes.isEmpty()) {
                    LOGGER.warning("  WARNING: No themes defined in config! Waves will not spawn mobs!");
                }

            } catch (Exception e) {
                LOGGER.warning("Failed to load config file, using defaults: " + e.getMessage());
                e.printStackTrace();
                config = createDefaultConfig();
                config.save();
            }
        } else {
            LOGGER.info("Config file not found, creating default config at: " + configFile.getAbsolutePath());
            config = createDefaultConfig();
            config.save();
        }

        instance = config;
        return config;
    }

    /**
     * Create a config with default values
     */
    private static CometConfig createDefaultConfig() {
        CometConfig config = new CometConfig();
        config.themes = DefaultThemes.generateDefaults();
        config.themeList = new ArrayList<>(config.themes.values());
        config.tierSettings = DefaultThemes.getDefaultTierSettings();
        config.zoneSpawnChances = ZoneSpawnChances.generateDefaults();
        config.themesLoaded = true;
        return config;
    }

    /**
     * Reload configuration from file
     */
    public static CometConfig reload() {
        LOGGER.info("Reloading configuration from file...");
        return load();
    }

    /**
     * Parse JSON configuration
     */
    private static CometConfig parseJson(String json) {
        CometConfig config = new CometConfig();

        try {
            // Parse spawn settings (check both old format and new nested format)
            String spawnBlock = extractJsonObject(json, "spawnSettings");
            String parseFrom = (spawnBlock != null) ? spawnBlock : json;

            // Parse spawn settings
            if (parseFrom.contains("\"minDelaySeconds\"")) {
                String value = extractJsonValue(parseFrom, "minDelaySeconds");
                if (value != null)
                    config.minDelaySeconds = Integer.parseInt(value);
            }
            if (parseFrom.contains("\"maxDelaySeconds\"")) {
                String value = extractJsonValue(parseFrom, "maxDelaySeconds");
                if (value != null)
                    config.maxDelaySeconds = Integer.parseInt(value);
            }
            if (parseFrom.contains("\"spawnChance\"")) {
                String value = extractJsonValue(parseFrom, "spawnChance");
                if (value != null)
                    config.spawnChance = Double.parseDouble(value);
            }
            if (parseFrom.contains("\"despawnTimeMinutes\"")) {
                String value = extractJsonValue(parseFrom, "despawnTimeMinutes");
                if (value != null)
                    config.despawnTimeMinutes = Double.parseDouble(value);
            }
            if (parseFrom.contains("\"minSpawnDistance\"")) {
                String value = extractJsonValue(parseFrom, "minSpawnDistance");
                if (value != null)
                    config.minSpawnDistance = Integer.parseInt(value);
            }
            if (parseFrom.contains("\"maxSpawnDistance\"")) {
                String value = extractJsonValue(parseFrom, "maxSpawnDistance");
                if (value != null)
                    config.maxSpawnDistance = Integer.parseInt(value);
            }
            if (parseFrom.contains("\"globalComets\"")) {
                String value = extractJsonValue(parseFrom, "globalComets");
                if (value != null)
                    config.globalComets = Boolean.parseBoolean(value);
            }

            // Parse themes using ThemeConfigParser
            config.themes = ThemeConfigParser.parseThemes(json);
            config.themeList = new ArrayList<>(config.themes.values());
            config.themesLoaded = !config.themes.isEmpty();

            // Parse tier settings
            config.tierSettings = ThemeConfigParser.parseTierSettings(json);

            // Parse reward settings
            config.rewardSettings = ThemeConfigParser.parseRewardSettings(json);
            LOGGER.info("Loaded reward settings for " + config.rewardSettings.size() + " tiers");

            // Parse zone spawn chances
            config.zoneSpawnChances = ThemeConfigParser.parseZoneSpawnChances(json);
            LOGGER.info("Loaded zone spawn chances for " + config.zoneSpawnChances.size() + " zones");

        } catch (Exception e) {
            LOGGER.warning("Error parsing JSON: " + e.getMessage());
            e.printStackTrace();
        }

        return config;
    }

    /**
     * Extract a JSON object by key
     */
    private static String extractJsonObject(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            int braceIndex = json.indexOf("{", colonIndex);
            if (braceIndex == -1)
                return null;

            int depth = 0;
            int endPos = braceIndex;
            boolean inString = false;

            for (int i = braceIndex; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (!inString) {
                    if (c == '{')
                        depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            endPos = i + 1;
                            break;
                        }
                    }
                }
            }

            return json.substring(braceIndex, endPos);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract a JSON value from a string
     */
    private static String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            int startIndex = colonIndex + 1;
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            int endIndex = startIndex;
            while (endIndex < json.length()) {
                char c = json.charAt(endIndex);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                endIndex++;
            }

            return json.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Save configuration to file
     */
    public void save() {
        File configFile = getConfigFile();

        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Ensure we have themes and tier settings to save
        if (themes.isEmpty()) {
            themes = DefaultThemes.generateDefaults();
            themeList = new ArrayList<>(themes.values());
        }
        if (tierSettings.isEmpty()) {
            tierSettings = DefaultThemes.getDefaultTierSettings();
        }
        if (zoneSpawnChances.isEmpty()) {
            zoneSpawnChances = ZoneSpawnChances.generateDefaults();
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            String json = ThemeConfigWriter.generateFullConfig(
                    minDelaySeconds, maxDelaySeconds, spawnChance,
                    despawnTimeMinutes, minSpawnDistance, maxSpawnDistance,
                    themes, tierSettings, rewardSettings, zoneSpawnChances);
            writer.write(json);
            writer.flush();
            LOGGER.info("Saved Comet Mod configuration to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.severe("Failed to save config file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply spawn settings to the spawn task
     */
    public void applyToSpawnTask(CometSpawnTask spawnTask) {
        if (spawnTask != null) {
            spawnTask.setMinDelaySeconds(minDelaySeconds);
            spawnTask.setMaxDelaySeconds(maxDelaySeconds);
            spawnTask.setSpawnChance(spawnChance);
            spawnTask.setMinSpawnDistance(minSpawnDistance);
            spawnTask.setMaxSpawnDistance(maxSpawnDistance);
            LOGGER.info("Applied config to spawn task: min=" + minDelaySeconds +
                    "s, max=" + maxDelaySeconds + "s, chance=" + (spawnChance * 100) +
                    "%, distance=" + minSpawnDistance + "-" + maxSpawnDistance + " blocks");
        }
    }

    // ========== Theme Access Methods ==========

    /**
     * Get all theme configurations
     */
    public Map<String, ThemeConfig> getThemes() {
        return themes;
    }

    /**
     * Get themes as an ordered list (for random selection)
     */
    public List<ThemeConfig> getThemeList() {
        return themeList;
    }

    /**
     * Get a theme by ID
     */
    public ThemeConfig getTheme(String id) {
        return themes.get(id);
    }

    /**
     * Get all themes available for a specific tier (excludes testOnly themes)
     *
     * @param tier The comet tier (1-4)
     * @return List of themes that can spawn naturally at this tier
     */
    public List<ThemeConfig> getThemesForTier(int tier) {
        List<ThemeConfig> result = new ArrayList<>();
        for (ThemeConfig theme : themeList) {
            // Skip testOnly themes - they can only be spawned manually
            if (theme.isTestOnly()) {
                continue;
            }
            if (theme.isAvailableForTier(tier)) {
                result.add(theme);
            }
        }
        return result;
    }

    /**
     * Get theme ID by index (for backwards compatibility)
     * 
     * @param index The theme index
     * @return Theme ID or null
     */
    public String getThemeIdByIndex(int index) {
        if (index >= 0 && index < themeList.size()) {
            return themeList.get(index).getId();
        }
        return null;
    }

    /**
     * Get theme index by ID
     * 
     * @param id The theme ID
     * @return Index or -1 if not found
     */
    public int getThemeIndex(String id) {
        for (int i = 0; i < themeList.size(); i++) {
            if (themeList.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get all theme display names
     */
    public String[] getThemeNames() {
        String[] names = new String[themeList.size()];
        for (int i = 0; i < themeList.size(); i++) {
            names[i] = themeList.get(i).getDisplayName();
        }
        return names;
    }

    /**
     * Get theme count
     */
    public int getThemeCount() {
        return themeList.size();
    }

    /**
     * Check if themes were loaded successfully
     */
    public boolean hasThemes() {
        return themesLoaded && !themes.isEmpty();
    }

    // ========== Tier Settings Access Methods ==========

    /**
     * Get tier settings for a specific tier
     * 
     * @param tier The tier number (1-4)
     */
    public TierSettings getTierSettings(int tier) {
        return tierSettings.getOrDefault(tier, TierSettings.getDefaultForTier(tier));
    }

    /**
     * Get all tier settings
     */
    public Map<Integer, TierSettings> getAllTierSettings() {
        return tierSettings;
    }

    /**
     * Get timeout for a tier in milliseconds
     */
    public long getTimeoutMillis(int tier) {
        return getTierSettings(tier).getTimeoutMillis();
    }

    /**
     * Get spawn radius range for a tier
     */
    public double[] getSpawnRadiusRange(int tier) {
        TierSettings ts = getTierSettings(tier);
        return new double[] { ts.getMinRadius(), ts.getMaxRadius() };
    }

    /**
     * Get reward settings for a specific tier
     * 
     * @param tier The tier number (1-4)
     */
    public TierRewards getTierRewards(int tier) {
        return rewardSettings.getOrDefault(tier, TierRewards.getDefaultForTier(tier));
    }

    /**
     * Get all reward settings
     */
    public Map<Integer, TierRewards> getAllRewardSettings() {
        return rewardSettings;
    }

    // ========== Zone Spawn Chances Access Methods ==========

    /**
     * Get zone spawn chances for a specific zone ID.
     * Falls back to "default" if zone not found.
     *
     * @param zoneId The zone ID (0, 1, 2, 3, etc.)
     * @return ZoneSpawnChances for that zone
     */
    public ZoneSpawnChances getZoneSpawnChances(int zoneId) {
        String zoneKey = String.valueOf(zoneId);
        ZoneSpawnChances chances = zoneSpawnChances.get(zoneKey);
        if (chances != null) {
            return chances;
        }
        // Fall back to "default" for unknown zones
        chances = zoneSpawnChances.get("default");
        if (chances != null) {
            return chances;
        }
        // Ultimate fallback - generate default for zone 4+
        return ZoneSpawnChances.getDefaultForZone(4);
    }

    /**
     * Get all zone spawn chances
     */
    public Map<String, ZoneSpawnChances> getAllZoneSpawnChances() {
        return zoneSpawnChances;
    }

    /**
     * Set zone spawn chances for a specific zone
     */
    public void setZoneSpawnChances(String zoneKey, ZoneSpawnChances chances) {
        zoneSpawnChances.put(zoneKey, chances);
    }

    /**
     * Add or update a theme in the config
     * Adds theme to all tiers (1-4) by default if no tiers specified
     */
    public void addOrUpdateTheme(ThemeConfig theme) {
        if (theme == null || theme.getId() == null) {
            LOGGER.warning("Cannot add null theme or theme with null ID");
            return;
        }

        // If theme has no tiers specified, add all tiers by default
        if (theme.getTiers() == null || theme.getTiers().isEmpty()) {
            List<Integer> allTiers = new ArrayList<>();
            for (int tier = 1; tier <= 4; tier++) {
                allTiers.add(tier);
            }
            theme.setTiers(allTiers);
        }

        // Add or update in themes map
        themes.put(theme.getId(), theme);

        // Update theme list
        themeList.removeIf(t -> t.getId().equals(theme.getId()));
        themeList.add(theme);

        themesLoaded = true;
        LOGGER.info("Added/updated theme: " + theme.getId());
    }

    /**
     * Remove a theme by ID
     */
    public void removeTheme(String themeId) {
        if (themeId == null) {
            return;
        }

        themes.remove(themeId);
        themeList.removeIf(t -> themeId.equals(t.getId()));

        LOGGER.info("Removed theme: " + themeId);
    }

    /**
     * Reload configuration from disk (renamed to avoid conflict)
     */
    public static void reloadConfig() {
        instance = null;
        load();
    }

}
