package com.cometmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import com.cometmod.config.ThemeConfig;
import com.cometmod.config.TierRewards;
import com.cometmod.config.TierSettings;
import com.cometmod.config.WaveEntry;

/**
 * Bridge class that provides config-based theme access while maintaining
 * compatibility with the existing CometWaveManager code structure.
 * 
 * This class centralizes all config lookups and provides methods that
 * match the existing patterns in CometWaveManager.
 */
public class WaveThemeProvider {

    private static final Logger LOGGER = Logger.getLogger("WaveThemeProvider");
    private static final Random RANDOM = new Random();

    /**
     * Select a random theme ID for the given comet tier.
     * Uses config-based tier availability instead of hardcoded nativeTier.
     * 
     * @param tier The comet tier
     * @return Theme ID (string) or null if no themes available
     */
    public static String selectTheme(CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null || !config.hasThemes()) {
            LOGGER.warning("No config or themes available!");
            return null;
        }

        int tierNum = getTierNumber(tier);
        List<ThemeConfig> availableThemes = config.getThemesForTier(tierNum);

        if (availableThemes.isEmpty()) {
            LOGGER.warning("No themes available for tier " + tier.getName());
            return null;
        }

        ThemeConfig selected = availableThemes.get(RANDOM.nextInt(availableThemes.size()));
        LOGGER.info("Selected theme: " + selected.getId() + " (" + selected.getDisplayName() + ") for tier "
                + tier.getName());
        return selected.getId();
    }

    /**
     * Get mob list for a theme with tier suffixes applied.
     * 
     * @param tier    The comet tier
     * @param themeId The theme ID
     * @return Array of mob NPC names ready to spawn, or null if theme not found
     */
    public static String[] getMobListForTheme(CometTier tier, String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) {
            LOGGER.warning("Theme not found: " + themeId);
            return null;
        }

        int tierNum = getTierNumber(tier);
        return theme.getMobIdsForTier(tierNum);
    }

    /**
     * Get boss list for a theme with tier suffixes applied.
     * 
     * @param tier    The comet tier
     * @param themeId The theme ID
     * @return List of boss NPC names ready to spawn
     */
    public static List<String> getBossesForTheme(CometTier tier, String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return new ArrayList<>();

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) {
            LOGGER.warning("Theme not found for boss lookup: " + themeId);
            return new ArrayList<>();
        }

        int tierNum = getTierNumber(tier);
        return theme.getBossIdsForTier(tierNum);
    }

    /**
     * Get the display name for a theme.
     * 
     * @param themeId The theme ID
     * @return Display name or "Unknown" if not found
     */
    public static String getThemeName(String themeId) {
        if (themeId == null)
            return "Unknown";

        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return themeId;

        ThemeConfig theme = config.getTheme(themeId);
        return theme != null ? theme.getDisplayName() : themeId;
    }

    /**
     * Get total mob count for wave 1 of a theme.
     * 
     * @param themeId The theme ID
     * @return Total mob count, or 5 as default
     */
    public static int getWaveMobCount(String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return 5;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null)
            return 5;

        int count = theme.getTotalMobCount();
        return count > 0 ? count : 5;
    }

    /**
     * Get timeout for a tier in milliseconds.
     * 
     * @param tier The comet tier
     * @return Timeout in milliseconds
     */
    public static long getTimeoutMillis(CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) {
            // Fallback defaults
            return TierSettings.getDefaultForTier(getTierNumber(tier)).getTimeoutMillis();
        }
        return config.getTimeoutMillis(getTierNumber(tier));
    }

    /**
     * Get spawn radius range for a tier.
     * 
     * @param tier The comet tier
     * @return [minRadius, maxRadius]
     */
    public static double[] getSpawnRadius(CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) {
            TierSettings ts = TierSettings.getDefaultForTier(getTierNumber(tier));
            return new double[] { ts.getMinRadius(), ts.getMaxRadius() };
        }
        return config.getSpawnRadiusRange(getTierNumber(tier));
    }

    /**
     * Get all theme IDs as array (for display/validation).
     * 
     * @return Array of all theme IDs
     */
    public static String[] getAllThemeIds() {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return new String[0];
        return config.getThemes().keySet().toArray(new String[0]);
    }

    /**
     * Get all theme display names.
     * 
     * @return Array of all theme display names
     */
    public static String[] getAllThemeNames() {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return new String[0];
        return config.getThemeNames();
    }

    /**
     * Find theme ID by name (case insensitive, partial match).
     * 
     * @param name The theme name to search for
     * @return Theme ID or null if not found
     */
    public static String findThemeByName(String name) {
        if (name == null || name.isEmpty())
            return null;

        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return null;

        String lowerName = name.toLowerCase();

        // First try exact ID match
        if (config.getTheme(lowerName) != null) {
            return lowerName;
        }

        // Try exact display name match
        for (ThemeConfig theme : config.getThemeList()) {
            if (theme.getDisplayName().equalsIgnoreCase(name)) {
                return theme.getId();
            }
        }

        // Try partial match on display name
        for (ThemeConfig theme : config.getThemeList()) {
            if (theme.getDisplayName().toLowerCase().contains(lowerName)) {
                return theme.getId();
            }
        }

        // Try partial match on ID
        for (ThemeConfig theme : config.getThemeList()) {
            if (theme.getId().toLowerCase().contains(lowerName)) {
                return theme.getId();
            }
        }

        return null;
    }

    /**
     * Check if a theme is available for a specific tier.
     * 
     * @param themeId The theme ID
     * @param tier    The comet tier
     * @return true if theme can spawn at this tier
     */
    public static boolean isThemeAvailableForTier(String themeId, CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return false;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null)
            return false;

        return theme.isAvailableForTier(getTierNumber(tier));
    }

    /**
     * Convert CometTier to tier number (1-4).
     */
    public static int getTierNumber(CometTier tier) {
        switch (tier) {
            case UNCOMMON:
                return 1;
            case RARE:
                return 2;
            case EPIC:
                return 3;
            case LEGENDARY:
                return 4;
            default:
                return 1;
        }
    }

    /**
     * Get boss stat multipliers for a specific boss in a theme at a tier.
     * 
     * @param themeId The theme ID
     * @param tier    The comet tier
     * @param bossId  The base boss ID (without tier suffix)
     * @return float[] {hpMult, damageMult, scaleMult, speedMult} or null if no
     *         multipliers
     */
    public static float[] getBossStatMultipliers(String themeId, CometTier tier, String bossId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null)
            return null;

        int tierNum = getTierNumber(tier);
        return theme.getBossMultipliers(tierNum, bossId);
    }

    /**
     * Get mob stat multipliers for a specific mob in a theme at a tier.
     * 
     * @param themeId   The theme ID
     * @param tier      The comet tier
     * @param baseMobId The base mob ID (without tier suffix)
     * @return float[] {hpMult, damageMult, scaleMult, speedMult} or null if no
     *         multipliers
     */
    public static float[] getMobStatMultipliers(String themeId, CometTier tier, String baseMobId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null)
            return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null)
            return null;

        int tierNum = getTierNumber(tier);
        return theme.getMobMultipliers(tierNum, baseMobId);
    }

    /**
     * Check if a theme has stat multipliers configured for a specific boss at a
     * tier.
     *
     * @param themeId The theme ID
     * @param tier    The comet tier
     * @param bossId  The base boss ID
     * @return true if multipliers are configured
     */
    public static boolean hasStatMultipliers(String themeId, CometTier tier, String bossId) {
        float[] bossMults = getBossStatMultipliers(themeId, tier, bossId);
        return bossMults != null;
    }

    // ========== MULTI-WAVE SUPPORT ==========

    /**
     * Check if a theme uses the multi-wave system.
     *
     * @param themeId The theme ID
     * @return true if theme has waves defined, false for legacy mobs/bosses system
     */
    public static boolean hasMultiWave(String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return false;

        ThemeConfig theme = config.getTheme(themeId);
        return theme != null && theme.hasMultiWave();
    }

    /**
     * Get the total number of waves for a theme.
     * Returns 2 for legacy themes (1 normal + 1 boss).
     *
     * @param themeId The theme ID
     * @return Total wave count
     */
    public static int getWaveCount(String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return 2;

        ThemeConfig theme = config.getTheme(themeId);
        return theme != null ? theme.getWaveCount() : 2;
    }

    /**
     * Get the number of normal waves for a theme.
     *
     * @param themeId The theme ID
     * @return Count of normal waves
     */
    public static int getNormalWaveCount(String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return 1;

        ThemeConfig theme = config.getTheme(themeId);
        return theme != null ? theme.getNormalWaveCount() : 1;
    }

    /**
     * Get the number of boss waves for a theme.
     *
     * @param themeId The theme ID
     * @return Count of boss waves
     */
    public static int getBossWaveCount(String themeId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return 1;

        ThemeConfig theme = config.getTheme(themeId);
        return theme != null ? theme.getBossWaveCount() : 1;
    }

    /**
     * Check if a specific wave is a normal wave (spawns mobs).
     *
     * @param themeId   The theme ID
     * @param waveIndex The wave index (0-based)
     * @return true if wave is normal, false if boss or invalid
     */
    public static boolean isWaveNormal(String themeId, int waveIndex) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return waveIndex == 0; // Legacy: wave 0 is normal

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return waveIndex == 0;

        WaveEntry wave = theme.getWave(waveIndex);
        return wave != null && wave.isNormalWave();
    }

    /**
     * Check if a specific wave is a boss wave.
     *
     * @param themeId   The theme ID
     * @param waveIndex The wave index (0-based)
     * @return true if wave is boss, false if normal or invalid
     */
    public static boolean isWaveBoss(String themeId, int waveIndex) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return waveIndex == 1; // Legacy: wave 1 is boss

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return waveIndex == 1;

        WaveEntry wave = theme.getWave(waveIndex);
        return wave != null && wave.isBossWave();
    }

    /**
     * Get mob list for a specific wave.
     *
     * @param tier      The comet tier
     * @param themeId   The theme ID
     * @param waveIndex The wave index (0-based)
     * @return Array of mob NPC names ready to spawn, or empty array if invalid
     */
    public static String[] getMobListForWave(CometTier tier, String themeId, int waveIndex) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) {
            // Legacy fallback for wave 0
            return waveIndex == 0 ? getMobListForTheme(tier, themeId) : new String[0];
        }

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return new String[0];

        WaveEntry wave = theme.getWave(waveIndex);
        if (wave == null) return new String[0];

        int tierNum = getTierNumber(tier);
        return wave.getMobIdsForTier(tierNum);
    }

    /**
     * Get boss list for a specific wave.
     *
     * @param tier      The comet tier
     * @param themeId   The theme ID
     * @param waveIndex The wave index (0-based)
     * @return List of boss NPC names ready to spawn
     */
    public static List<String> getBossesForWave(CometTier tier, String themeId, int waveIndex) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) {
            // Legacy fallback for wave 1
            return waveIndex == 1 ? getBossesForTheme(tier, themeId) : new ArrayList<>();
        }

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return new ArrayList<>();

        WaveEntry wave = theme.getWave(waveIndex);
        if (wave == null) return new ArrayList<>();

        int tierNum = getTierNumber(tier);
        return wave.getBossIdsForTier(tierNum);
    }

    /**
     * Get mob stat multipliers for a specific wave.
     *
     * @param themeId   The theme ID
     * @param tier      The comet tier
     * @param waveIndex The wave index (0-based)
     * @param baseMobId The base mob ID
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public static float[] getMobStatMultipliersForWave(String themeId, CometTier tier, int waveIndex, String baseMobId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return null;

        int tierNum = getTierNumber(tier);

        // For multi-wave themes, use wave-specific multipliers
        if (theme.hasMultiWave()) {
            return theme.getMobMultipliersForWave(waveIndex, tierNum, baseMobId);
        }

        // Legacy: use theme-level multipliers
        return theme.getMobMultipliers(tierNum, baseMobId);
    }

    /**
     * Get boss stat multipliers for a specific wave.
     *
     * @param themeId   The theme ID
     * @param tier      The comet tier
     * @param waveIndex The wave index (0-based)
     * @param bossId    The base boss ID
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public static float[] getBossStatMultipliersForWave(String themeId, CometTier tier, int waveIndex, String bossId) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return null;

        int tierNum = getTierNumber(tier);

        // For multi-wave themes, use wave-specific multipliers
        if (theme.hasMultiWave()) {
            return theme.getBossMultipliersForWave(waveIndex, tierNum, bossId);
        }

        // Legacy: use theme-level multipliers
        return theme.getBossMultipliers(tierNum, bossId);
    }

    // ========== REWARD OVERRIDE SUPPORT ==========

    /**
     * Check if a theme has reward override for a specific tier.
     *
     * @param themeId The theme ID
     * @param tier    The comet tier
     * @return true if theme has custom rewards for this tier
     */
    public static boolean hasRewardOverride(String themeId, CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return false;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return false;

        int tierNum = getTierNumber(tier);
        return theme.hasRewardOverrideForTier(tierNum);
    }

    /**
     * Get reward override for a theme at a specific tier.
     *
     * @param themeId The theme ID
     * @param tier    The comet tier
     * @return TierRewards override or null if not set
     */
    public static TierRewards getRewardOverride(String themeId, CometTier tier) {
        CometConfig config = CometConfig.getInstance();
        if (config == null) return null;

        ThemeConfig theme = config.getTheme(themeId);
        if (theme == null) return null;

        int tierNum = getTierNumber(tier);
        return theme.getRewardOverrideForTier(tierNum);
    }
}
