package com.cometmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Configuration for all rewards in a specific tier.
 * Contains guaranteed drops and bonus drops with chances.
 */
public class TierRewards {

    private List<RewardEntry> drops;
    private List<RewardEntry> bonusDrops;

    public TierRewards() {
        this.drops = new ArrayList<>();
        this.bonusDrops = new ArrayList<>();
    }

    public TierRewards(List<RewardEntry> drops, List<RewardEntry> bonusDrops) {
        this.drops = drops != null ? drops : new ArrayList<>();
        this.bonusDrops = bonusDrops != null ? bonusDrops : new ArrayList<>();
    }

    // Getters
    public List<RewardEntry> getDrops() {
        return drops;
    }

    public List<RewardEntry> getBonusDrops() {
        return bonusDrops;
    }

    // Setters
    public void setDrops(List<RewardEntry> drops) {
        this.drops = drops != null ? drops : new ArrayList<>();
    }

    public void setBonusDrops(List<RewardEntry> bonusDrops) {
        this.bonusDrops = bonusDrops != null ? bonusDrops : new ArrayList<>();
    }

    public void addDrop(RewardEntry drop) {
        this.drops.add(drop);
    }

    public void addBonusDrop(RewardEntry bonusDrop) {
        this.bonusDrops.add(bonusDrop);
    }

    /**
     * Generate all items for this tier's rewards
     * 
     * @param random         Random instance for count/chance calculations
     * @param allItems       List to add ItemStacks to
     * @param droppedItemIds List to add display strings to
     */
    public void generateRewards(Random random,
            List<com.hypixel.hytale.server.core.inventory.ItemStack> allItems,
            List<String> droppedItemIds) {

        // Process guaranteed drops
        for (RewardEntry drop : drops) {
            if (drop.shouldDrop(random)) {
                int count = drop.getRandomCount(random);
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack(drop.getId(), count));
                droppedItemIds.add(drop.getDisplayName() + " x" + count);
            }
        }

        // Process bonus drops
        for (RewardEntry bonusDrop : bonusDrops) {
            if (bonusDrop.shouldDrop(random)) {
                int count = bonusDrop.getRandomCount(random);
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack(bonusDrop.getId(), count));
                droppedItemIds.add(bonusDrop.getDisplayName() + " x" + count + " (bonus)");
            }
        }
    }

    @Override
    public String toString() {
        return "TierRewards{drops=" + drops.size() + ", bonusDrops=" + bonusDrops.size() + "}";
    }

    // ========== Static factory methods for default rewards ==========

    public static TierRewards getDefaultTier1() {
        TierRewards rewards = new TierRewards();
        rewards.addDrop(new RewardEntry("Ingredient_Bar_Copper", 5, 7, 100, "Copper Ingots"));
        rewards.addDrop(new RewardEntry("Ingredient_Leather_Light", 2, 3, 100, "Light Leather"));
        rewards.addDrop(new RewardEntry("Potion_Health_Lesser", 1, 2, 100, "Lesser Health Potion"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb", 3, 4, 100, "Bombs"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb_Potion_Poison", 1, 1, 100, "Poison Potion Bomb"));
        return rewards;
    }

    public static TierRewards getDefaultTier2() {
        TierRewards rewards = new TierRewards();
        rewards.addDrop(new RewardEntry("Ingredient_Bar_Iron", 5, 7, 100, "Iron Ingots"));
        rewards.addDrop(new RewardEntry("Ingredient_Leather_Medium", 2, 3, 100, "Medium Leather"));
        rewards.addDrop(new RewardEntry("Potion_Health", 1, 2, 100, "Potion of Health"));
        rewards.addDrop(new RewardEntry("Ingredient_Fire_Essence", 3, 4, 100, "Essence of Fire"));
        rewards.addDrop(new RewardEntry("Ingredient_Fabric_Scrap_Shadoweave", 5, 5, 100, "Shadoweave Scraps"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb", 4, 5, 100, "Bombs"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb_Potion_Poison", 1, 2, 100, "Poison Potion Bomb"));

        // Bonus drops (35% chance each)
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Copper", 2, 4, 35, "Copper Ingots"));
        rewards.addBonusDrop(new RewardEntry("Potion_Health_Lesser", 1, 1, 35, "Lesser Health Potion"));
        return rewards;
    }

    public static TierRewards getDefaultTier3() {
        TierRewards rewards = new TierRewards();
        // Primary ore (50/50 Cobalt or Thorium)
        rewards.addDrop(new RewardEntry("Ingredient_Bar_Cobalt", 5, 7, 50, "Cobalt Ingots"));
        rewards.addDrop(new RewardEntry("Ingredient_Bar_Thorium", 5, 7, 50, "Thorium Ingots"));
        rewards.addDrop(new RewardEntry("Ingredient_Leather_Heavy", 2, 3, 100, "Heavy Leather"));
        rewards.addDrop(new RewardEntry("Potion_Health_Greater", 1, 2, 100, "Greater Health Potion"));
        rewards.addDrop(new RewardEntry("Ingredient_Fire_Essence", 3, 4, 100, "Essence of Fire"));
        rewards.addDrop(new RewardEntry("Ingredient_Fabric_Scrap_Shadoweave", 5, 5, 100, "Shadoweave Scraps"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb", 5, 6, 100, "Bombs"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb_Potion_Poison", 2, 3, 100, "Poison Potion Bomb"));

        // Bonus drops (30% chance each)
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Copper", 2, 4, 30, "Copper Ingots"));
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Iron", 2, 4, 30, "Iron Ingots"));
        rewards.addBonusDrop(new RewardEntry("Potion_Health_Lesser", 1, 1, 30, "Lesser Health Potion"));
        rewards.addBonusDrop(new RewardEntry("Potion_Health", 1, 1, 30, "Potion of Health"));
        return rewards;
    }

    public static TierRewards getDefaultTier4() {
        TierRewards rewards = new TierRewards();
        rewards.addDrop(new RewardEntry("Ingredient_Bar_Adamantite", 5, 8, 100, "Adamantite Ingots"));
        rewards.addDrop(new RewardEntry("Ingredient_Leather_Heavy", 3, 4, 100, "Heavy Leather"));
        rewards.addDrop(new RewardEntry("Potion_Health_Greater", 2, 3, 100, "Greater Health Potion"));
        rewards.addDrop(new RewardEntry("Ingredient_Fire_Essence", 4, 6, 100, "Essence of Fire"));
        rewards.addDrop(new RewardEntry("Ingredient_Fabric_Scrap_Shadoweave", 8, 10, 100, "Shadoweave Scraps"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb", 6, 8, 100, "Bombs"));
        rewards.addDrop(new RewardEntry("Weapon_Bomb_Potion_Poison", 3, 4, 100, "Poison Potion Bomb"));

        // Bonus drops (25% chance each)
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Copper", 3, 5, 25, "Copper Ingots"));
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Iron", 3, 5, 25, "Iron Ingots"));
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Cobalt", 2, 4, 25, "Cobalt Ingots"));
        rewards.addBonusDrop(new RewardEntry("Ingredient_Bar_Thorium", 2, 4, 25, "Thorium Ingots"));
        rewards.addBonusDrop(new RewardEntry("Potion_Health_Lesser", 1, 2, 25, "Lesser Health Potion"));
        rewards.addBonusDrop(new RewardEntry("Potion_Health", 1, 2, 25, "Potion of Health"));
        return rewards;
    }

    public static TierRewards getDefaultForTier(int tier) {
        switch (tier) {
            case 1:
                return getDefaultTier1();
            case 2:
                return getDefaultTier2();
            case 3:
                return getDefaultTier3();
            case 4:
                return getDefaultTier4();
            default:
                return getDefaultTier1();
        }
    }
}
