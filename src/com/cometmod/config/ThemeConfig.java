package com.cometmod.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single wave theme.
 * Defines which mobs spawn, which tiers can use this theme, and boss options.
 */
public class ThemeConfig {

    private String id;
    private String displayName;
    private List<Integer> tiers;
    private List<MobEntry> mobs;
    private List<BossEntry> bosses;
    private boolean useTierSuffix;
    private boolean randomBossSelection;
    private boolean testOnly;  // If true, theme won't spawn naturally (manual spawn only)

    // Multi-wave support: if waves is non-empty, it overrides mobs/bosses
    private List<WaveEntry> waves;

    // Per-tier reward overrides: if set for a tier, uses these instead of global tier rewards
    private Map<Integer, TierRewards> rewardOverride;

    public ThemeConfig() {
        this.id = "";
        this.displayName = "Unknown";
        this.tiers = new ArrayList<>();
        this.mobs = new ArrayList<>();
        this.bosses = new ArrayList<>();
        this.useTierSuffix = true;
        this.randomBossSelection = false;
        this.testOnly = false;
        this.waves = new ArrayList<>();
        this.rewardOverride = new LinkedHashMap<>();
    }

    public ThemeConfig(String id, String displayName, List<Integer> tiers,
            List<MobEntry> mobs, List<BossEntry> bosses, boolean useTierSuffix) {
        this.id = id;
        this.displayName = displayName;
        this.tiers = tiers != null ? tiers : new ArrayList<>();
        this.mobs = mobs != null ? mobs : new ArrayList<>();
        this.bosses = bosses != null ? bosses : new ArrayList<>();
        this.useTierSuffix = useTierSuffix;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<Integer> getTiers() {
        return tiers;
    }

    public List<MobEntry> getMobs() {
        return mobs;
    }

    public List<BossEntry> getBosses() {
        return bosses;
    }

    public boolean useTierSuffix() {
        return useTierSuffix;
    }

    public boolean useRandomBossSelection() {
        return randomBossSelection;
    }

    public boolean isTestOnly() {
        return testOnly;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setTiers(List<Integer> tiers) {
        this.tiers = tiers;
    }

    public void setMobs(List<MobEntry> mobs) {
        this.mobs = mobs;
    }

    public void setBosses(List<BossEntry> bosses) {
        this.bosses = bosses;
    }

    public void setUseTierSuffix(boolean useTierSuffix) {
        this.useTierSuffix = useTierSuffix;
    }

    public void setRandomBossSelection(boolean randomBossSelection) {
        this.randomBossSelection = randomBossSelection;
    }

    public void setTestOnly(boolean testOnly) {
        this.testOnly = testOnly;
    }

    // ========== REWARD OVERRIDE ==========

    public Map<Integer, TierRewards> getRewardOverride() {
        return rewardOverride;
    }

    public void setRewardOverride(Map<Integer, TierRewards> rewardOverride) {
        this.rewardOverride = rewardOverride != null ? rewardOverride : new LinkedHashMap<>();
    }

    /**
     * Get reward override for a specific tier.
     *
     * @param tier The tier (1-4)
     * @return TierRewards override or null if not set
     */
    public TierRewards getRewardOverrideForTier(int tier) {
        return rewardOverride != null ? rewardOverride.get(tier) : null;
    }

    /**
     * Set reward override for a specific tier.
     *
     * @param tier    The tier (1-4)
     * @param rewards The rewards to use for this tier
     */
    public void setRewardOverrideForTier(int tier, TierRewards rewards) {
        if (rewardOverride == null) {
            rewardOverride = new LinkedHashMap<>();
        }
        rewardOverride.put(tier, rewards);
    }

    /**
     * Check if this theme has reward overrides for any tier.
     *
     * @return true if at least one tier has reward override
     */
    public boolean hasRewardOverride() {
        return rewardOverride != null && !rewardOverride.isEmpty();
    }

    /**
     * Check if this theme has reward override for a specific tier.
     *
     * @param tier The tier (1-4)
     * @return true if this tier has a reward override
     */
    public boolean hasRewardOverrideForTier(int tier) {
        return rewardOverride != null && rewardOverride.containsKey(tier);
    }

    // ========== MULTI-WAVE SUPPORT ==========

    public List<WaveEntry> getWaves() {
        return waves;
    }

    public void setWaves(List<WaveEntry> waves) {
        this.waves = waves != null ? waves : new ArrayList<>();
    }

    /**
     * Check if this theme uses the multi-wave system.
     * If true, the waves list should be used instead of mobs/bosses.
     *
     * @return true if waves are defined and non-empty
     */
    public boolean hasMultiWave() {
        return waves != null && !waves.isEmpty();
    }

    /**
     * Get the total number of waves in this theme.
     * Returns 2 for legacy themes (1 normal + 1 boss), or waves.size() for multi-wave.
     *
     * @return Total wave count
     */
    public int getWaveCount() {
        if (hasMultiWave()) {
            return waves.size();
        }
        return 2; // Legacy: 1 normal wave + 1 boss wave
    }

    /**
     * Get a specific wave entry by index (0-based).
     * For legacy themes, wave 0 returns a synthesized normal wave from mobs,
     * and wave 1 returns a synthesized boss wave from bosses.
     *
     * @param waveIndex The wave index (0-based)
     * @return WaveEntry for the requested wave, or null if invalid index
     */
    public WaveEntry getWave(int waveIndex) {
        if (hasMultiWave()) {
            if (waveIndex >= 0 && waveIndex < waves.size()) {
                return waves.get(waveIndex);
            }
            return null;
        }

        // Legacy compatibility: synthesize WaveEntry from mobs/bosses
        if (waveIndex == 0) {
            // Wave 0: normal wave with mobs
            WaveEntry legacyNormal = new WaveEntry(WaveEntry.WaveType.NORMAL);
            legacyNormal.setMobs(mobs);
            return legacyNormal;
        } else if (waveIndex == 1) {
            // Wave 1: boss wave with bosses
            WaveEntry legacyBoss = new WaveEntry(WaveEntry.WaveType.BOSS);
            legacyBoss.setBosses(bosses);
            legacyBoss.setRandomBossSelection(randomBossSelection);
            return legacyBoss;
        }
        return null;
    }

    /**
     * Get the number of normal waves in this theme.
     *
     * @return Count of normal waves
     */
    public int getNormalWaveCount() {
        if (hasMultiWave()) {
            int count = 0;
            for (WaveEntry wave : waves) {
                if (wave.isNormalWave()) count++;
            }
            return count;
        }
        return 1; // Legacy: 1 normal wave
    }

    /**
     * Get the number of boss waves in this theme.
     *
     * @return Count of boss waves
     */
    public int getBossWaveCount() {
        if (hasMultiWave()) {
            int count = 0;
            for (WaveEntry wave : waves) {
                if (wave.isBossWave()) count++;
            }
            return count;
        }
        return 1; // Legacy: 1 boss wave
    }

    /**
     * Check if this theme is available for a specific comet tier
     * 
     * @param tier The comet tier (1-4)
     * @return true if this theme can spawn at this tier
     */
    public boolean isAvailableForTier(int tier) {
        return tiers.contains(tier);
    }

    /**
     * Get total mob count for wave 1
     * @param tier The comet tier (1-4) for tier-based counts
     */
    public int getTotalMobCount(int tier) {
        int total = 0;
        for (MobEntry mob : mobs) {
            total += mob.getCountForTier(tier);
        }
        return total;
    }

    /**
     * Get total mob count for wave 1 (legacy method without tier)
     * Uses simple count value
     */
    public int getTotalMobCount() {
        int total = 0;
        for (MobEntry mob : mobs) {
            total += mob.getCount();
        }
        return total;
    }

    /**
     * Get all mob IDs with tier suffix applied if needed
     * 
     * @param cometTier The comet tier (1-4) for suffix
     * @return Array of mob IDs ready to spawn
     */
    /**
     * Get all mob IDs (base IDs, no suffixes)
     *
     * @param cometTier The comet tier (1-4) for tier-based counts
     * @return Array of mob IDs ready to spawn
     */
    public String[] getMobIdsForTier(int cometTier) {
        List<String> result = new ArrayList<>();

        for (MobEntry mob : mobs) {
            String mobId = mob.getId();
            // Get count for this tier (uses tier-based count if available, otherwise simple count)
            int count = mob.getCountForTier(cometTier);
            System.out.println("[ThemeConfig] " + id + " tier " + cometTier + ": " + mobId + " = " + count + " (tierCounts: " + mob.getTierCounts() + ", simpleCount: " + mob.getCount() + ")");
            // Add the mob ID 'count' times
            for (int i = 0; i < count; i++) {
                result.add(mobId);
            }
        }

        System.out.println("[ThemeConfig] Total for tier " + cometTier + ": " + result.size() + " mobs");
        return result.toArray(new String[0]);
    }

    /**
     * Get boss IDs (base IDs, no suffixes)
     *
     * @param cometTier The comet tier (unused now)
     * @return List of boss IDs ready to spawn
     */
    public List<String> getBossIdsForTier(int cometTier) {
        List<String> result = new ArrayList<>();

        // If random boss selection is enabled, pick only 1 random boss
        if (randomBossSelection && !bosses.isEmpty()) {
            // Filter bosses that have stats for this tier
            List<BossEntry> validBosses = new ArrayList<>();
            for (BossEntry boss : bosses) {
                if (boss.getMultipliersForTier(cometTier) != null) {
                    validBosses.add(boss);
                }
            }

            // Pick one random boss from valid ones
            if (!validBosses.isEmpty()) {
                BossEntry selectedBoss = validBosses.get(new java.util.Random().nextInt(validBosses.size()));
                result.add(selectedBoss.getId());
                System.out.println("[ThemeConfig] Random boss selection for " + id + " tier " + cometTier + ": " + selectedBoss.getId());
            }
        } else {
            // Spawn all bosses
            for (BossEntry boss : bosses) {
                result.add(boss.getId());
            }
        }

        return result;
    }

    /**
     * Validate that this theme has required data
     *
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (id == null || id.isEmpty()) {
            errors.add("Theme ID is missing");
        }
        if (displayName == null || displayName.isEmpty()) {
            errors.add("Theme '" + id + "' has no displayName");
        }
        if (tiers == null || tiers.isEmpty()) {
            errors.add("Theme '" + id + "' has no tiers defined");
        }

        // Multi-wave themes only need waves defined, legacy themes need mobs/bosses
        if (hasMultiWave()) {
            // Validate each wave has content
            for (int i = 0; i < waves.size(); i++) {
                WaveEntry wave = waves.get(i);
                if (wave.isNormalWave() && (wave.getMobs() == null || wave.getMobs().isEmpty())) {
                    errors.add("Theme '" + id + "' wave " + i + " (normal) has no mobs defined");
                }
                if (wave.isBossWave() && (wave.getBosses() == null || wave.getBosses().isEmpty())) {
                    errors.add("Theme '" + id + "' wave " + i + " (boss) has no bosses defined");
                }
            }
        } else {
            // Legacy validation
            if (mobs == null || mobs.isEmpty()) {
                errors.add("Theme '" + id + "' has no mobs defined");
            }
            if (bosses == null || bosses.isEmpty()) {
                errors.add("Theme '" + id + "' has no bosses defined");
            }
        }

        return errors;
    }

    // ========== STAT MULTIPLIERS ==========
    // Maps are removed as multipliers are now stored in MobEntry/BossEntry objects.

    /**
     * Get boss multipliers for a specific boss at a tier
     * 
     * @param tier   The tier (1-4)
     * @param bossId The base boss ID (without tier suffix)
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public float[] getBossMultipliers(int tier, String bossId) {
        for (BossEntry boss : bosses) {
            if (boss.getId().equals(bossId)) {
                return boss.getMultipliersForTier(tier);
            }
        }
        return null;
    }

    /**
     * Get mob multipliers for a specific mob at a tier
     * 
     * @param tier      The tier (1-4)
     * @param baseMobId The base mob ID (without tier suffix)
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public float[] getMobMultipliers(int tier, String baseMobId) {
        for (MobEntry mob : mobs) {
            if (mob.getId().equals(baseMobId)) {
                return mob.getMultipliersForTier(tier);
            }
        }
        return null;
    }

    /**
     * Check if this theme has any stat multipliers defined
     * 
     * @return true if stat multipliers are configured
     */
    public boolean hasStatMultipliers() {
        for (MobEntry mob : mobs) {
            if (!mob.getMultipliers().isEmpty())
                return true;
        }
        for (BossEntry boss : bosses) {
            if (!boss.getMultipliers().isEmpty())
                return true;
        }
        return false;
    }

    /**
     * Get mob multipliers for a specific wave and mob.
     *
     * @param waveIndex Wave index (0-based)
     * @param tier      The tier (1-4)
     * @param baseMobId The base mob ID
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public float[] getMobMultipliersForWave(int waveIndex, int tier, String baseMobId) {
        WaveEntry wave = getWave(waveIndex);
        if (wave != null) {
            return wave.getMobMultipliers(tier, baseMobId);
        }
        return null;
    }

    /**
     * Get boss multipliers for a specific wave and boss.
     *
     * @param waveIndex Wave index (0-based)
     * @param tier      The tier (1-4)
     * @param bossId    The base boss ID
     * @return float[] {hp, damage, scale, speed} or null if not set
     */
    public float[] getBossMultipliersForWave(int waveIndex, int tier, String bossId) {
        WaveEntry wave = getWave(waveIndex);
        if (wave != null) {
            return wave.getBossMultipliers(tier, bossId);
        }
        return null;
    }

    @Override
    public String toString() {
        if (hasMultiWave()) {
            return "ThemeConfig{id='" + id + "', displayName='" + displayName +
                    "', tiers=" + tiers + ", waves=" + waves.size() +
                    " (" + getNormalWaveCount() + " normal, " + getBossWaveCount() + " boss)" +
                    ", hasMultipliers=" + hasStatMultipliers() + "}";
        }
        return "ThemeConfig{id='" + id + "', displayName='" + displayName +
                "', tiers=" + tiers + ", mobs=" + mobs.size() + ", bosses=" + bosses.size() +
                ", hasMultipliers=" + hasStatMultipliers() + "}";
    }
}
