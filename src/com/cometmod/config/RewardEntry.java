package com.cometmod.config;

/**
 * Configuration for a single reward item drop.
 * Specifies the item ID, count range, chance (as percentage 0-100), and display
 * name.
 */
public class RewardEntry {

    private String id;
    private int minCount;
    private int maxCount;
    private double chance; // 0 to 100 (percentage), defaults to 100 (always drops)
    private String displayName;

    public RewardEntry() {
        this.id = "";
        this.minCount = 1;
        this.maxCount = 1;
        this.chance = 100.0; // 100% by default
        this.displayName = "";
    }

    public RewardEntry(String id, int minCount, int maxCount, String displayName) {
        this.id = id;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.chance = 100.0; // 100% by default
        this.displayName = displayName;
    }

    public RewardEntry(String id, int minCount, int maxCount, double chance, String displayName) {
        this.id = id;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.chance = chance; // Now expects 0-100
        this.displayName = displayName;
    }

    // Getters
    public String getId() {
        return id;
    }

    public int getMinCount() {
        return minCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public double getChance() {
        return chance;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : id;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setMinCount(int minCount) {
        this.minCount = Math.max(1, minCount);
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = Math.max(this.minCount, maxCount);
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(100.0, chance));
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get a random count between minCount and maxCount (inclusive)
     */
    public int getRandomCount(java.util.Random random) {
        if (minCount == maxCount) {
            return minCount;
        }
        return minCount + random.nextInt(maxCount - minCount + 1);
    }

    /**
     * Check if this reward should drop based on its chance (percentage 0-100)
     */
    public boolean shouldDrop(java.util.Random random) {
        if (chance >= 100.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return (random.nextDouble() * 100.0) < chance;
    }

    /**
     * Get the chance as a display string (e.g., "35%")
     */
    public String getChanceDisplay() {
        if (chance >= 100.0) {
            return "100%";
        }
        return String.format("%.0f%%", chance);
    }

    @Override
    public String toString() {
        return "RewardEntry{id='" + id + "', count=" + minCount + "-" + maxCount +
                ", chance=" + getChanceDisplay() + ", displayName='" + displayName + "'}";
    }
}
