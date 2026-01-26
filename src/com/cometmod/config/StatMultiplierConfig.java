package com.cometmod.config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration for stat multipliers applied to comet mobs.
 * 
 * Supports per-tier and per-mob multipliers for:
 * - HP (health points)
 * - Damage
 * - Scale (model size)
 */
public class StatMultiplierConfig {

    private static final Logger LOGGER = Logger.getLogger("StatMultiplierConfig");

    // Theme ID -> Tier -> Multipliers
    private final Map<String, Map<Integer, TierMultipliers>> themeMultipliers = new HashMap<>();

    /**
     * Set multipliers for a theme at a specific tier
     */
    public void setTierMultipliers(String themeId, int tier, TierMultipliers multipliers) {
        themeMultipliers.computeIfAbsent(themeId, k -> new HashMap<>()).put(tier, multipliers);
    }

    /**
     * Get multipliers for a theme at a specific tier
     */
    public TierMultipliers getTierMultipliers(String themeId, int tier) {
        Map<Integer, TierMultipliers> tierMap = themeMultipliers.get(themeId);
        if (tierMap != null) {
            return tierMap.get(tier);
        }
        return null;
    }

    /**
     * Get boss multipliers for a theme at a tier
     */
    public MobMultipliers getBossMultipliers(String themeId, int tier) {
        TierMultipliers tierMults = getTierMultipliers(themeId, tier);
        if (tierMults != null) {
            return tierMults.bossMultipliers;
        }
        return null;
    }

    /**
     * Get specific mob multipliers for a theme at a tier
     */
    public MobMultipliers getMobMultipliers(String themeId, int tier, String mobId) {
        TierMultipliers tierMults = getTierMultipliers(themeId, tier);
        if (tierMults != null && tierMults.mobMultipliers != null) {
            return tierMults.mobMultipliers.get(mobId);
        }
        return null;
    }

    /**
     * Check if a theme has any multipliers configured
     */
    public boolean hasMultipliers(String themeId) {
        return themeMultipliers.containsKey(themeId);
    }

    /**
     * Parse multipliers from JSON object string
     * Expected format:
     * {
     * "statMultipliers": {
     * "2": {
     * "boss": { "hp": 2.0, "damage": 1.5, "scale": 1.2 },
     * "mobs": {
     * "Yeti": { "hp": 4.0, "damage": 2.0, "scale": 1.5 }
     * }
     * }
     * }
     * }
     */
    public static StatMultiplierConfig parseFromThemeJson(String themeId, String json) {
        StatMultiplierConfig config = new StatMultiplierConfig();

        try {
            // Find "statMultipliers" section
            int statMultStart = json.indexOf("\"statMultipliers\"");
            if (statMultStart < 0) {
                return config; // No multipliers configured
            }

            // Find the opening brace for statMultipliers
            int openBrace = json.indexOf("{", statMultStart);
            if (openBrace < 0)
                return config;

            // Find matching closing brace
            int closeBrace = findMatchingBrace(json, openBrace);
            if (closeBrace < 0)
                return config;

            String statMultJson = json.substring(openBrace + 1, closeBrace);

            // Parse each tier
            for (int tier = 1; tier <= 4; tier++) {
                String tierKey = "\"" + tier + "\"";
                int tierStart = statMultJson.indexOf(tierKey);
                if (tierStart < 0)
                    continue;

                int tierOpenBrace = statMultJson.indexOf("{", tierStart);
                if (tierOpenBrace < 0)
                    continue;

                int tierCloseBrace = findMatchingBrace(statMultJson, tierOpenBrace);
                if (tierCloseBrace < 0)
                    continue;

                String tierJson = statMultJson.substring(tierOpenBrace + 1, tierCloseBrace);

                TierMultipliers tierMults = new TierMultipliers();

                // Parse boss multipliers
                int bossStart = tierJson.indexOf("\"boss\"");
                if (bossStart >= 0) {
                    int bossOpenBrace = tierJson.indexOf("{", bossStart);
                    if (bossOpenBrace >= 0) {
                        int bossCloseBrace = findMatchingBrace(tierJson, bossOpenBrace);
                        if (bossCloseBrace >= 0) {
                            String bossJson = tierJson.substring(bossOpenBrace + 1, bossCloseBrace);
                            tierMults.bossMultipliers = parseMobMultipliers(bossJson);
                        }
                    }
                }

                // Parse per-mob multipliers
                int mobsStart = tierJson.indexOf("\"mobs\"");
                if (mobsStart >= 0) {
                    int mobsOpenBrace = tierJson.indexOf("{", mobsStart);
                    if (mobsOpenBrace >= 0) {
                        int mobsCloseBrace = findMatchingBrace(tierJson, mobsOpenBrace);
                        if (mobsCloseBrace >= 0) {
                            String mobsJson = tierJson.substring(mobsOpenBrace + 1, mobsCloseBrace);
                            tierMults.mobMultipliers = parseMobsSection(mobsJson);
                        }
                    }
                }

                if (tierMults.bossMultipliers != null || tierMults.mobMultipliers != null) {
                    config.setTierMultipliers(themeId, tier, tierMults);
                    LOGGER.info("[StatMultiplierConfig] Loaded multipliers for theme " + themeId + " tier " + tier);
                }
            }

        } catch (Exception e) {
            LOGGER.warning(
                    "[StatMultiplierConfig] Error parsing multipliers for theme " + themeId + ": " + e.getMessage());
        }

        return config;
    }

    private static MobMultipliers parseMobMultipliers(String json) {
        MobMultipliers mults = new MobMultipliers();

        mults.hp = parseFloatValue(json, "hp", 1.0f);
        mults.damage = parseFloatValue(json, "damage", 1.0f);
        mults.scale = parseFloatValue(json, "scale", 1.0f);

        return mults;
    }

    private static Map<String, MobMultipliers> parseMobsSection(String json) {
        Map<String, MobMultipliers> result = new HashMap<>();

        // Find each mob entry: "MobName": { ... }
        int pos = 0;
        while (pos < json.length()) {
            int quoteStart = json.indexOf("\"", pos);
            if (quoteStart < 0)
                break;

            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteEnd < 0)
                break;

            String mobName = json.substring(quoteStart + 1, quoteEnd);

            int openBrace = json.indexOf("{", quoteEnd);
            if (openBrace < 0)
                break;

            int closeBrace = findMatchingBrace(json, openBrace);
            if (closeBrace < 0)
                break;

            String mobJson = json.substring(openBrace + 1, closeBrace);
            result.put(mobName, parseMobMultipliers(mobJson));

            pos = closeBrace + 1;
        }

        return result;
    }

    private static float parseFloatValue(String json, String key, float defaultValue) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyPos = json.indexOf(searchKey);
            if (keyPos < 0)
                return defaultValue;

            int colonPos = json.indexOf(":", keyPos);
            if (colonPos < 0)
                return defaultValue;

            // Find the value (number)
            int valueStart = colonPos + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }

            int valueEnd = valueStart;
            while (valueEnd < json.length()) {
                char c = json.charAt(valueEnd);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c))
                    break;
                valueEnd++;
            }

            String valueStr = json.substring(valueStart, valueEnd).trim();
            return Float.parseFloat(valueStr);

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int findMatchingBrace(String json, int openPos) {
        int depth = 1;
        for (int i = openPos + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{')
                depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    /**
     * Holds multipliers for a specific tier
     */
    public static class TierMultipliers {
        public MobMultipliers bossMultipliers;
        public Map<String, MobMultipliers> mobMultipliers;
    }

    /**
     * Holds HP, damage, and scale multipliers for a mob
     */
    public static class MobMultipliers {
        public float hp = 1.0f;
        public float damage = 1.0f;
        public float scale = 1.0f;

        @Override
        public String toString() {
            return "MobMultipliers{hp=" + hp + ", damage=" + damage + ", scale=" + scale + "}";
        }
    }
}
