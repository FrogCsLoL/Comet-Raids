package com.cometmod.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates default theme configurations based on the original hardcoded
 * values.
 * This is used when no config file exists or when themes section is missing.
 */
public class DefaultThemes {

    /**
     * Generate all default themes matching the original CometWaveManager hardcoded
     * values
     * 
     * @return Map of theme ID to ThemeConfig
     */
    public static Map<String, ThemeConfig> generateDefaults() {
        Map<String, ThemeConfig> themes = new LinkedHashMap<>();

        // Tier 1 Native Themes (available at tiers 1, 2)
        themes.put("skeleton", createTheme("skeleton", "Skeleton Horde",
                Arrays.asList(1, 2),
                Arrays.asList(
                        new MobEntry("Skeleton_Soldier", 3),
                        new MobEntry("Skeleton_Archer", 1),
                        new MobEntry("Skeleton_Archmage", 1)),
                Arrays.asList(
                        new BossEntry("Bear_Polar"),
                        new BossEntry("Wolf_Black"))));

        themes.put("goblin", createTheme("goblin", "Goblin Gang",
                Arrays.asList(1, 2),
                Arrays.asList(
                        new MobEntry("Goblin_Scrapper", 2),
                        new MobEntry("Goblin_Miner", 2),
                        new MobEntry("Goblin_Lobber", 1)),
                Arrays.asList(
                        new BossEntry("Bear_Polar"),
                        new BossEntry("Wolf_Black"))));

        themes.put("spider", createTheme("spider", "Spider Swarm",
                Arrays.asList(1, 2),
                Arrays.asList(
                        new MobEntry("Spider", 5)),
                Arrays.asList(
                        new BossEntry("Spider_Broodmother"))));

        // Tier 2 Native Themes (available at tiers 1, 2, 3)
        themes.put("trork", createTheme("trork", "Trork Warband",
                Arrays.asList(1, 2, 3),
                Arrays.asList(
                        new MobEntry("Trork_Warrior", 1),
                        new MobEntry("Trork_Hunter", 1),
                        new MobEntry("Trork_Mauler", 1),
                        new MobEntry("Trork_Shaman", 1),
                        new MobEntry("Trork_Brawler", 1)),
                Arrays.asList(
                        new BossEntry("Trork_Chieftain"))));

        themes.put("skeleton_sand", createTheme("skeleton_sand", "Sand Skeleton Legion",
                Arrays.asList(1, 2, 3),
                Arrays.asList(
                        new MobEntry("Skeleton_Sand_Archer", 1),
                        new MobEntry("Skeleton_Sand_Assassin", 1),
                        new MobEntry("Skeleton_Sand_Guard", 1),
                        new MobEntry("Skeleton_Sand_Mage", 1),
                        new MobEntry("Skeleton_Sand_Ranger", 1)),
                Arrays.asList(
                        new BossEntry("Bear_Grizzly"),
                        new BossEntry("Skeleton_Burnt_Alchemist"))));

        themes.put("sabertooth", createTheme("sabertooth", "Sabertooth Pack",
                Arrays.asList(1, 2, 3),
                Arrays.asList(
                        new MobEntry("Tiger_Sabertooth", 4)),
                Arrays.asList(
                        new BossEntry("Bear_Grizzly"),
                        new BossEntry("Skeleton_Burnt_Alchemist"))));

        // Tier 3 Native Themes (available at tiers 2, 3, 4)
        themes.put("outlander", createTheme("outlander", "Outlander Cult",
                Arrays.asList(2, 3, 4),
                Arrays.asList(
                        new MobEntry("Outlander_Berserker", 2),
                        new MobEntry("Outlander_Cultist", 1),
                        new MobEntry("Outlander_Hunter", 1),
                        new MobEntry("Outlander_Stalker", 1),
                        new MobEntry("Outlander_Brute", 1)),
                Arrays.asList(
                        new BossEntry("Werewolf"),
                        new BossEntry("Yeti"))));

        themes.put("leopard", createTheme("leopard", "Snow Leopard Pride",
                Arrays.asList(2, 3, 4),
                Arrays.asList(
                        new MobEntry("Leopard_Snow", 5)),
                Arrays.asList(
                        new BossEntry("Werewolf"))));

        // Tier 4 Native Themes (available at tiers 3, 4)
        themes.put("toad", createTheme("toad", "Magma Toads",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Toad_Rhino_Magma", 3)),
                Arrays.asList(
                        new BossEntry("Shadow_Knight"),
                        new BossEntry("Zombie_Aberrant"))));

        themes.put("skeleton_burnt", createTheme("skeleton_burnt", "Burnt Legion",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Skeleton_Burnt_Archer", 1),
                        new MobEntry("Skeleton_Burnt_Gunner", 1),
                        new MobEntry("Skeleton_Burnt_Knight", 1),
                        new MobEntry("Skeleton_Burnt_Lancer", 2)),
                Arrays.asList(
                        new BossEntry("Skeleton_Burnt_Praetorian"))));

        // Void - Special: available at tiers 1, 2, 3 (excluded from 4)
        themes.put("void", createTheme("void", "Voidspawn",
                Arrays.asList(1, 2, 3),
                Arrays.asList(
                        new MobEntry("Crawler_Void", 2),
                        new MobEntry("Spectre_Void", 2)),
                Arrays.asList(
                        new BossEntry("Spawn_Void"))));

        // Legendary Themes (tier 4 only or 3-4)
        themes.put("ice", createTheme("ice", "Legendary Ice",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Yeti", 1),
                        new MobEntry("Bear_Polar", 2),
                        new MobEntry("Golem_Crystal_Frost", 1),
                        new MobEntry("Leopard_Snow", 2)),
                Arrays.asList(
                        new BossEntry("Spirit_Frost"))));

        themes.put("lava", createTheme("lava", "Legendary Lava",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Emberwulf", 1),
                        new MobEntry("Golem_Firesteel", 2),
                        new MobEntry("Spirit_Ember", 1)),
                Arrays.asList(
                        new BossEntry("Toad_Rhino_Magma"))));

        themes.put("earth", createTheme("earth", "Legendary Earth",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Golem_Crystal_Earth", 1),
                        new MobEntry("Bear_Grizzly", 2),
                        new MobEntry("Hyena", 4)),
                Arrays.asList(
                        new BossEntry("Hedera"))));

        themes.put("undead_rare", createTheme("undead_rare", "Rare Undead",
                Arrays.asList(1, 2, 3),
                Arrays.asList(
                        new MobEntry("Pig_Undead", 2),
                        new MobEntry("Cow_Undead", 1),
                        new MobEntry("Chicken_Undead", 2)),
                Arrays.asList(
                        new BossEntry("Golem_Crystal_Thunder"))));

        themes.put("undead_legendary", createTheme("undead_legendary", "Legendary Undead",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Pig_Undead", 8),
                        new MobEntry("Cow_Undead", 4),
                        new MobEntry("Chicken_Undead", 6),
                        new MobEntry("Hound_Bleached", 3)),
                Arrays.asList(
                        new BossEntry("Wraith"))));

        themes.put("zombie", createTheme("zombie", "Zombie Aberration",
                Arrays.asList(3, 4),
                Arrays.asList(
                        new MobEntry("Zombie_Aberrant_Small", 5)),
                Arrays.asList(
                        new BossEntry("Zombie_Aberrant"))));

        return themes;
    }

    private static ThemeConfig createTheme(String id, String displayName,
            List<Integer> tiers, List<MobEntry> mobs, List<BossEntry> bosses) {
        return new ThemeConfig(id, displayName, tiers, mobs, bosses, true);
    }

    /**
     * Get default tier settings for all 4 tiers
     * 
     * @return Map of tier number (1-4) to TierSettings
     */
    public static Map<Integer, TierSettings> getDefaultTierSettings() {
        Map<Integer, TierSettings> settings = new LinkedHashMap<>();
        settings.put(1, TierSettings.TIER1_DEFAULTS);
        settings.put(2, TierSettings.TIER2_DEFAULTS);
        settings.put(3, TierSettings.TIER3_DEFAULTS);
        settings.put(4, TierSettings.TIER4_DEFAULTS);
        return settings;
    }
}
