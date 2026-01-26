package com.cometmod.config;

/**
 * Configuration for a boss in a wave.
 * Specifies the NPC ID and whether to apply tier suffix.
 */
public class BossEntry {

    private String id;
    // tier -> {hp, damage, scale, speed}
    private java.util.Map<Integer, float[]> multipliers = new java.util.HashMap<>();

    public BossEntry() {
        this.id = "";
    }

    public BossEntry(String id) {
        this.id = id;
    }

    // Getters
    public String getId() {
        return id;
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

    public void setMultipliers(java.util.Map<Integer, float[]> multipliers) {
        this.multipliers = multipliers;
    }

    public void addMultiplier(int tier, float hp, float damage, float scale, float speed) {
        this.multipliers.put(tier, new float[] { hp, damage, scale, speed });
    }

    @Override
    public String toString() {
        return "BossEntry{id='" + id + "', multipliers=" + multipliers.size() + "}";
    }
}
