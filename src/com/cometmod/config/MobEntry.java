package com.cometmod.config;

/**
 * Configuration for a single mob type in a wave.
 * Specifies the NPC ID, count, and whether to apply tier suffix.
 */
public class MobEntry {

    private String id;
    private int count;
    // Tier-based counts (optional) - if null, use simple count
    private java.util.Map<Integer, Integer> tierCounts = null;
    // tier -> {hp, damage, scale, speed}
    private java.util.Map<Integer, float[]> multipliers = new java.util.HashMap<>();

    public MobEntry() {
        this.id = "";
        this.count = 1;
    }

    public MobEntry(String id, int count) {
        this.id = id;
        this.count = count;
    }

    // Getters
    public String getId() {
        return id;
    }

    public int getCount() {
        return count;
    }

    /**
     * Get the count for a specific tier.
     * If tier-based counts are defined, uses those; otherwise uses simple count.
     */
    public int getCountForTier(int tier) {
        if (tierCounts != null) {
            // If tierCounts map exists, only return the count if this tier is defined
            // Otherwise return 0 (mob doesn't spawn at this tier)
            return tierCounts.getOrDefault(tier, 0);
        }
        // No tier-based counts, use simple count for all tiers
        return count;
    }

    public java.util.Map<Integer, Integer> getTierCounts() {
        return tierCounts;
    }

    public java.util.Map<Integer, float[]> getMultipliers() {
        return multipliers;
    }

    public float[] getMultipliersForTier(int tier) {
        return multipliers.get(tier);
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    public void setTierCounts(java.util.Map<Integer, Integer> tierCounts) {
        this.tierCounts = tierCounts;
    }

    public void setMultipliers(java.util.Map<Integer, float[]> multipliers) {
        this.multipliers = multipliers;
    }

    public void addMultiplier(int tier, float hp, float damage, float scale, float speed) {
        this.multipliers.put(tier, new float[] { hp, damage, scale, speed });
    }

    @Override
    public String toString() {
        String countStr = tierCounts != null ? "tierCounts=" + tierCounts : "count=" + count;
        return "MobEntry{id='" + id + "', " + countStr + ", multipliers=" + multipliers.size() + "}";
    }
}
