package com.cometmod.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a single wave in a multi-wave comet encounter.
 * Each wave can be either "normal" (spawns mobs) or "boss" (spawns bosses).
 *
 * This allows flexible wave configuration like:
 * - 2 normal waves then 1 boss wave
 * - 3 normal waves with escalating difficulty
 * - 1 normal wave followed by 2 boss waves
 */
public class WaveEntry {

    public enum WaveType {
        NORMAL,
        BOSS
    }

    private WaveType type;
    private List<MobEntry> mobs;
    private List<BossEntry> bosses;
    private boolean randomBossSelection;

    public WaveEntry() {
        this.type = WaveType.NORMAL;
        this.mobs = new ArrayList<>();
        this.bosses = new ArrayList<>();
        this.randomBossSelection = false;
    }

    public WaveEntry(WaveType type) {
        this.type = type;
        this.mobs = new ArrayList<>();
        this.bosses = new ArrayList<>();
        this.randomBossSelection = false;
    }

    // Getters
    public WaveType getType() {
        return type;
    }

    public List<MobEntry> getMobs() {
        return mobs;
    }

    public List<BossEntry> getBosses() {
        return bosses;
    }

    public boolean useRandomBossSelection() {
        return randomBossSelection;
    }

    public boolean isNormalWave() {
        return type == WaveType.NORMAL;
    }

    public boolean isBossWave() {
        return type == WaveType.BOSS;
    }

    // Setters
    public void setType(WaveType type) {
        this.type = type;
    }

    public void setType(String typeStr) {
        if ("boss".equalsIgnoreCase(typeStr)) {
            this.type = WaveType.BOSS;
        } else {
            this.type = WaveType.NORMAL;
        }
    }

    public void setMobs(List<MobEntry> mobs) {
        this.mobs = mobs != null ? mobs : new ArrayList<>();
    }

    public void setBosses(List<BossEntry> bosses) {
        this.bosses = bosses != null ? bosses : new ArrayList<>();
    }

    public void setRandomBossSelection(boolean randomBossSelection) {
        this.randomBossSelection = randomBossSelection;
    }

    /**
     * Get all mob IDs for this wave at the given tier.
     *
     * @param cometTier The comet tier (1-4)
     * @return Array of mob IDs ready to spawn
     */
    public String[] getMobIdsForTier(int cometTier) {
        List<String> result = new ArrayList<>();

        for (MobEntry mob : mobs) {
            String mobId = mob.getId();
            int count = mob.getCountForTier(cometTier);
            for (int i = 0; i < count; i++) {
                result.add(mobId);
            }
        }

        return result.toArray(new String[0]);
    }

    /**
     * Get boss IDs for this wave at the given tier.
     *
     * @param cometTier The comet tier (1-4)
     * @return List of boss IDs ready to spawn
     */
    public List<String> getBossIdsForTier(int cometTier) {
        List<String> result = new ArrayList<>();

        if (randomBossSelection && !bosses.isEmpty()) {
            List<BossEntry> validBosses = new ArrayList<>();
            for (BossEntry boss : bosses) {
                if (boss.getMultipliersForTier(cometTier) != null) {
                    validBosses.add(boss);
                }
            }

            if (!validBosses.isEmpty()) {
                BossEntry selectedBoss = validBosses.get(new java.util.Random().nextInt(validBosses.size()));
                result.add(selectedBoss.getId());
            } else if (!bosses.isEmpty()) {
                result.add(bosses.get(new java.util.Random().nextInt(bosses.size())).getId());
            }
        } else {
            for (BossEntry boss : bosses) {
                result.add(boss.getId());
            }
        }

        return result;
    }

    /**
     * Get mob multipliers for a specific mob in this wave.
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
     * Get boss multipliers for a specific boss in this wave.
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
     * Get total mob count for this wave at a tier.
     */
    public int getTotalMobCount(int tier) {
        int total = 0;
        for (MobEntry mob : mobs) {
            total += mob.getCountForTier(tier);
        }
        return total;
    }

    @Override
    public String toString() {
        return "WaveEntry{type=" + type + ", mobs=" + mobs.size() + ", bosses=" + bosses.size() + "}";
    }
}
