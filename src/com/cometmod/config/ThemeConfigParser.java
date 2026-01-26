package com.cometmod.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON parser for theme configurations.
 * Handles nested objects and arrays for theme definitions.
 * 
 * Note: This is a simple parser without external dependencies.
 * For production use, consider using a JSON library like Gson.
 */
public class ThemeConfigParser {

    private static final Logger LOGGER = Logger.getLogger("ThemeConfigParser");

    /**
     * Parse themes from a JSON string
     * 
     * @param json The full config JSON content
     * @return Map of theme ID to ThemeConfig
     */
    public static Map<String, ThemeConfig> parseThemes(String json) {
        Map<String, ThemeConfig> themes = new LinkedHashMap<>();

        try {
            // Find the "themes" object
            String themesBlock = extractJsonObject(json, "themes");
            if (themesBlock == null || themesBlock.isEmpty()) {
                LOGGER.info("No themes block found in config, using defaults");
                return DefaultThemes.generateDefaults();
            }

            // Parse each theme within the themes block
            // Look for pattern: "themeId": { ... }
            Pattern themePattern = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{");
            Matcher matcher = themePattern.matcher(themesBlock);

            while (matcher.find()) {
                String themeId = matcher.group(1);
                int startPos = matcher.end() - 1; // Position of opening brace
                String themeJson = extractObjectFromPosition(themesBlock, startPos);

                if (themeJson != null) {
                    ThemeConfig theme = parseTheme(themeId, themeJson);
                    if (theme != null) {
                        themes.put(themeId, theme);
                        LOGGER.info("Parsed theme: " + themeId + " - " + theme.getDisplayName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing themes: " + e.getMessage());
            e.printStackTrace();
        }

        if (themes.isEmpty()) {
            LOGGER.warning("No themes parsed, falling back to defaults");
            return DefaultThemes.generateDefaults();
        }

        return themes;
    }

    /**
     * Parse a single theme from its JSON block
     */
    private static ThemeConfig parseTheme(String id, String json) {
        try {
            ThemeConfig theme = new ThemeConfig();
            theme.setId(id);

            // Parse displayName
            String displayName = extractStringValue(json, "displayName");
            theme.setDisplayName(displayName != null ? displayName : id);

            // Parse useTierSuffix (default true)
            Boolean useTierSuffix = extractBooleanValue(json, "useTierSuffix");
            theme.setUseTierSuffix(useTierSuffix != null ? useTierSuffix : true);

            // Parse randomBossSelection (default false)
            Boolean randomBossSelection = extractBooleanValue(json, "randomBossSelection");
            theme.setRandomBossSelection(randomBossSelection != null ? randomBossSelection : false);

            // Parse testOnly (default false) - if true, theme won't spawn naturally
            Boolean testOnly = extractBooleanValue(json, "testOnly");
            theme.setTestOnly(testOnly != null ? testOnly : false);

            // Parse tiers array
            List<Integer> tiers = extractIntArray(json, "tiers");
            theme.setTiers(tiers);

            // Parse mobs array
            List<MobEntry> mobs = parseMobs(json);
            theme.setMobs(mobs);

            // Parse bosses array
            List<BossEntry> bosses = parseBosses(json);
            theme.setBosses(bosses);

            // Parse multi-wave array (optional, overrides mobs/bosses if present)
            List<WaveEntry> waves = parseWaves(json);
            theme.setWaves(waves);

            // Parse statMultipliers if present
            parseStatMultipliers(json, theme);

            // Parse rewardOverride if present (per-tier custom loot)
            parseRewardOverride(json, theme);

            return theme;
        } catch (Exception e) {
            LOGGER.warning("Error parsing theme '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse statMultipliers section from theme JSON
     */
    private static void parseStatMultipliers(String json, ThemeConfig theme) {
        try {
            String statMultBlock = extractJsonObject(json, "statMultipliers");
            if (statMultBlock == null) {
                return; // No stat multipliers configured
            }

            // Parse each tier's multipliers
            for (int tier = 1; tier <= 4; tier++) {
                String tierJson = extractJsonObject(statMultBlock, String.valueOf(tier));
                if (tierJson == null)
                    continue;

                // Parse per-boss multipliers (new map format)
                String bossesJson = extractJsonObject(tierJson, "bosses");
                if (bossesJson != null) {
                    java.util.regex.Pattern bossPattern = java.util.regex.Pattern
                            .compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{");
                    java.util.regex.Matcher bossMatcher = bossPattern.matcher(bossesJson);

                    while (bossMatcher.find()) {
                        String bossId = bossMatcher.group(1);
                        if (bossId.equals("bosses"))
                            continue;

                        int startPos = bossMatcher.end() - 1;
                        String bossMultJson = extractObjectFromPosition(bossesJson, startPos);

                        if (bossMultJson != null) {
                            Double hp = extractDoubleValue(bossMultJson, "hp");
                            Double damage = extractDoubleValue(bossMultJson, "damage");
                            Double scale = extractDoubleValue(bossMultJson, "scale");
                            Double speed = extractDoubleValue(bossMultJson, "speed");

                            // Find boss entry and add multiplier
                            for (BossEntry boss : theme.getBosses()) {
                                if (boss.getId().equals(bossId)) {
                                    boss.addMultiplier(tier,
                                            hp != null ? hp.floatValue() : 1.0f,
                                            damage != null ? damage.floatValue() : 1.0f,
                                            scale != null ? scale.floatValue() : 1.0f,
                                            speed != null ? speed.floatValue() : 1.0f);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // Fallback to legacy single boss format
                    String bossJson = extractJsonObject(tierJson, "boss");
                    if (bossJson != null) {
                        Double hp = extractDoubleValue(bossJson, "hp");
                        Double damage = extractDoubleValue(bossJson, "damage");
                        Double scale = extractDoubleValue(bossJson, "scale");
                        Double speed = extractDoubleValue(bossJson, "speed");

                        // Apply to ALL bosses in this theme if using legacy format
                        for (BossEntry be : theme.getBosses()) {
                            be.addMultiplier(tier,
                                    hp != null ? hp.floatValue() : 1.0f,
                                    damage != null ? damage.floatValue() : 1.0f,
                                    scale != null ? scale.floatValue() : 1.0f,
                                    speed != null ? speed.floatValue() : 1.0f);
                        }
                    }
                }

                // Parse per-mob multipliers
                String mobsJson = extractJsonObject(tierJson, "mobs");
                if (mobsJson != null) {
                    // Find each mob entry: "MobName": { ... }
                    java.util.regex.Pattern mobPattern = java.util.regex.Pattern
                            .compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{");
                    java.util.regex.Matcher mobMatcher = mobPattern.matcher(mobsJson);

                    while (mobMatcher.find()) {
                        String mobId = mobMatcher.group(1);
                        // Skip the parent "mobs" key itself
                        if (mobId.equals("mobs"))
                            continue;

                        int startPos = mobMatcher.end() - 1;
                        String mobMultJson = extractObjectFromPosition(mobsJson, startPos);

                        if (mobMultJson != null) {
                            Double hp = extractDoubleValue(mobMultJson, "hp");
                            Double damage = extractDoubleValue(mobMultJson, "damage");
                            Double scale = extractDoubleValue(mobMultJson, "scale");
                            Double speed = extractDoubleValue(mobMultJson, "speed");

                            // Find mob entry and add multiplier
                            for (MobEntry mob : theme.getMobs()) {
                                if (mob.getId().equals(mobId)) {
                                    mob.addMultiplier(tier,
                                            hp != null ? hp.floatValue() : 1.0f,
                                            damage != null ? damage.floatValue() : 1.0f,
                                            scale != null ? scale.floatValue() : 1.0f,
                                            speed != null ? speed.floatValue() : 1.0f);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing stat multipliers: " + e.getMessage());
        }
    }

    /**
     * Parse rewardOverride section from theme JSON.
     * Format:
     * "rewardOverride": {
     *   "2": { "drops": [...], "bonusDrops": [...] },
     *   "3": { "drops": [...], "bonusDrops": [...] }
     * }
     */
    private static void parseRewardOverride(String json, ThemeConfig theme) {
        try {
            String rewardBlock = extractJsonObject(json, "rewardOverride");
            if (rewardBlock == null) {
                return; // No reward override configured
            }

            Map<Integer, TierRewards> rewardOverride = new LinkedHashMap<>();

            // Parse each tier's rewards: "1": {...}, "2": {...}, etc.
            for (int tier = 1; tier <= 4; tier++) {
                String tierKey = String.valueOf(tier);
                String tierJson = extractJsonObject(rewardBlock, tierKey);

                if (tierJson != null) {
                    TierRewards tr = new TierRewards();

                    // Parse drops array
                    List<RewardEntry> drops = parseRewardEntries(tierJson, "drops");
                    tr.setDrops(drops);

                    // Parse bonusDrops array
                    List<RewardEntry> bonusDrops = parseRewardEntries(tierJson, "bonusDrops");
                    tr.setBonusDrops(bonusDrops);

                    rewardOverride.put(tier, tr);
                    LOGGER.info("Parsed reward override for theme '" + theme.getId() + "' tier " + tier +
                            ": " + drops.size() + " drops, " + bonusDrops.size() + " bonus drops");
                }
            }

            if (!rewardOverride.isEmpty()) {
                theme.setRewardOverride(rewardOverride);
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing reward override: " + e.getMessage());
        }
    }

    /**
     * Parse mobs array from theme JSON
     */
    private static List<MobEntry> parseMobs(String json) {
        List<MobEntry> mobs = new ArrayList<>();

        try {
            String mobsArray = extractJsonArray(json, "mobs");
            if (mobsArray == null)
                return mobs;

            // Parse each mob object in the array
            List<String> mobObjects = extractArrayObjects(mobsArray);
            for (String mobJson : mobObjects) {
                MobEntry mob = new MobEntry();

                String id = extractStringValue(mobJson, "id");
                if (id != null)
                    mob.setId(id);

                // Try to parse count as integer first
                Integer count = extractIntValue(mobJson, "count");
                if (count != null) {
                    mob.setCount(count);
                } else {
                    // Try to parse as tier-based count object: "count": { "1": 4, "2": 5 }
                    String countObj = extractJsonObject(mobJson, "count");
                    if (countObj != null) {
                        Map<Integer, Integer> tierCounts = new LinkedHashMap<>();
                        for (int tier = 1; tier <= 4; tier++) {
                            Integer tierCount = extractIntValue(countObj, String.valueOf(tier));
                            if (tierCount != null) {
                                tierCounts.put(tier, tierCount);
                            }
                        }
                        if (!tierCounts.isEmpty()) {
                            mob.setTierCounts(tierCounts);
                            // Set simple count to max value as fallback
                            int maxCount = tierCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
                            mob.setCount(maxCount);
                        }
                    }
                }

                // Parse inline stats: "stats": { "1": { "hp": 1.0, ... } }
                parseInlineStats(mobJson, mob);

                if (id != null && !id.isEmpty()) {
                    mobs.add(mob);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing mobs: " + e.getMessage());
        }

        return mobs;
    }

    /**
     * Parse waves array from theme JSON for multi-wave support.
     * Format:
     * "waves": [
     *   { "type": "normal", "mobs": [...] },
     *   { "type": "normal", "mobs": [...] },
     *   { "type": "boss", "bosses": [...], "randomBossSelection": true }
     * ]
     */
    private static List<WaveEntry> parseWaves(String json) {
        List<WaveEntry> waves = new ArrayList<>();

        try {
            String wavesArray = extractJsonArray(json, "waves");
            if (wavesArray == null) {
                return waves; // No waves defined, will use legacy mobs/bosses
            }

            // Parse each wave object in the array
            List<String> waveObjects = extractArrayObjects(wavesArray);
            for (String waveJson : waveObjects) {
                WaveEntry wave = new WaveEntry();

                // Parse type (default to "normal")
                String type = extractStringValue(waveJson, "type");
                if (type != null) {
                    wave.setType(type);
                }

                // Parse mobs array (for normal waves)
                List<MobEntry> waveMobs = parseMobs(waveJson);
                wave.setMobs(waveMobs);

                // Parse bosses array (for boss waves)
                List<BossEntry> waveBosses = parseBosses(waveJson);
                wave.setBosses(waveBosses);

                // Parse randomBossSelection (default false)
                Boolean randomBossSelection = extractBooleanValue(waveJson, "randomBossSelection");
                wave.setRandomBossSelection(randomBossSelection != null ? randomBossSelection : false);

                waves.add(wave);
            }

            LOGGER.info("Parsed " + waves.size() + " waves for multi-wave theme");
        } catch (Exception e) {
            LOGGER.warning("Error parsing waves: " + e.getMessage());
        }

        return waves;
    }

    /**
     * Parse bosses array from theme JSON
     */
    private static List<BossEntry> parseBosses(String json) {
        List<BossEntry> bosses = new ArrayList<>();

        try {
            String bossesArray = extractJsonArray(json, "bosses");
            if (bossesArray == null)
                return bosses;

            // Check if bosses are simple strings or objects
            if (bossesArray.contains("{")) {
                // Object format: { "id": "Boss" }
                List<String> bossObjects = extractArrayObjects(bossesArray);
                for (String bossJson : bossObjects) {
                    BossEntry boss = new BossEntry();

                    String id = extractStringValue(bossJson, "id");
                    if (id != null)
                        boss.setId(id);

                    // Parse inline stats
                    parseInlineStats(bossJson, boss);

                    if (id != null && !id.isEmpty()) {
                        bosses.add(boss);
                    }
                }
            } else {
                // Simple string format: ["Boss1", "Boss2"]
                List<String> bossNames = extractStringArray(bossesArray);
                for (String name : bossNames) {
                    if (name != null && !name.isEmpty()) {
                        bosses.add(new BossEntry(name));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing bosses: " + e.getMessage());
        }

        return bosses;
    }

    /**
     * Helper to parse "stats" object from a mob/boss entry JSON
     */
    private static void parseInlineStats(String json, Object entry) {
        try {
            String statsJson = extractJsonObject(json, "stats");
            if (statsJson == null)
                return;

            for (int tier = 1; tier <= 4; tier++) {
                String tierJson = extractJsonObject(statsJson, String.valueOf(tier));
                if (tierJson != null) {
                    Double hp = extractDoubleValue(tierJson, "hp");
                    Double damage = extractDoubleValue(tierJson, "damage");
                    Double scale = extractDoubleValue(tierJson, "scale");
                    Double speed = extractDoubleValue(tierJson, "speed");

                    float fh = hp != null ? hp.floatValue() : 1.0f;
                    float fd = damage != null ? damage.floatValue() : 1.0f;
                    float fs = scale != null ? scale.floatValue() : 1.0f;
                    float fsp = speed != null ? speed.floatValue() : 1.0f;

                    if (entry instanceof MobEntry) {
                        ((MobEntry) entry).addMultiplier(tier, fh, fd, fs, fsp);
                    } else if (entry instanceof BossEntry) {
                        ((BossEntry) entry).addMultiplier(tier, fh, fd, fs, fsp);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Parse tier settings from config JSON
     */
    public static Map<Integer, TierSettings> parseTierSettings(String json) {
        Map<Integer, TierSettings> settings = new LinkedHashMap<>();

        try {
            String tierBlock = extractJsonObject(json, "tierSettings");
            if (tierBlock == null) {
                return DefaultThemes.getDefaultTierSettings();
            }

            // Parse each tier: "1": { ... }, "2": { ... }
            for (int tier = 1; tier <= 4; tier++) {
                String tierKey = String.valueOf(tier);
                String tierJson = extractJsonObject(tierBlock, tierKey);

                if (tierJson != null) {
                    TierSettings ts = new TierSettings();

                    Integer timeout = extractIntValue(tierJson, "timeoutSeconds");
                    if (timeout != null)
                        ts.setTimeoutSeconds(timeout);

                    Double minRadius = extractDoubleValue(tierJson, "minRadius");
                    if (minRadius != null)
                        ts.setMinRadius(minRadius);

                    Double maxRadius = extractDoubleValue(tierJson, "maxRadius");
                    if (maxRadius != null)
                        ts.setMaxRadius(maxRadius);

                    settings.put(tier, ts);
                } else {
                    // Use defaults for this tier
                    settings.put(tier, TierSettings.getDefaultForTier(tier));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing tier settings: " + e.getMessage());
            return DefaultThemes.getDefaultTierSettings();
        }

        return settings;
    }

    /**
     * Parse reward settings from config JSON
     */
    public static Map<Integer, TierRewards> parseRewardSettings(String json) {
        Map<Integer, TierRewards> rewards = new LinkedHashMap<>();

        try {
            String rewardBlock = extractJsonObject(json, "rewardSettings");
            if (rewardBlock == null) {
                // Return defaults
                for (int tier = 1; tier <= 4; tier++) {
                    rewards.put(tier, TierRewards.getDefaultForTier(tier));
                }
                return rewards;
            }

            // Parse each tier: "1": { ... }, "2": { ... }
            for (int tier = 1; tier <= 4; tier++) {
                String tierKey = String.valueOf(tier);
                String tierJson = extractJsonObject(rewardBlock, tierKey);

                if (tierJson != null) {
                    TierRewards tr = new TierRewards();

                    // Parse drops array
                    List<RewardEntry> drops = parseRewardEntries(tierJson, "drops");
                    tr.setDrops(drops);

                    // Parse bonusDrops array
                    List<RewardEntry> bonusDrops = parseRewardEntries(tierJson, "bonusDrops");
                    tr.setBonusDrops(bonusDrops);

                    rewards.put(tier, tr);
                } else {
                    // Use defaults for this tier
                    rewards.put(tier, TierRewards.getDefaultForTier(tier));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing reward settings: " + e.getMessage());
            // Return defaults
            for (int tier = 1; tier <= 4; tier++) {
                rewards.put(tier, TierRewards.getDefaultForTier(tier));
            }
        }

        return rewards;
    }

    /**
     * Parse reward entries (drops or bonusDrops) from tier JSON
     */
    private static List<RewardEntry> parseRewardEntries(String json, String key) {
        List<RewardEntry> entries = new ArrayList<>();

        try {
            String entriesArray = extractJsonArray(json, key);
            if (entriesArray == null)
                return entries;

            // Parse each reward object in the array
            List<String> rewardObjects = extractArrayObjects(entriesArray);
            for (String rewardJson : rewardObjects) {
                RewardEntry reward = new RewardEntry();

                String id = extractStringValue(rewardJson, "id");
                if (id != null)
                    reward.setId(id);

                Integer minCount = extractIntValue(rewardJson, "minCount");
                if (minCount != null)
                    reward.setMinCount(minCount);

                Integer maxCount = extractIntValue(rewardJson, "maxCount");
                if (maxCount != null)
                    reward.setMaxCount(maxCount);

                Double chance = extractDoubleValue(rewardJson, "chance");
                if (chance != null)
                    reward.setChance(chance);

                String displayName = extractStringValue(rewardJson, "displayName");
                if (displayName != null)
                    reward.setDisplayName(displayName);

                if (id != null && !id.isEmpty()) {
                    entries.add(reward);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing reward entries for '" + key + "': " + e.getMessage());
        }

        return entries;
    }

    /**
     * Parse zone spawn chances from config JSON.
     * Format:
     * "zoneSpawnChances": {
     *   "0": { "tier1": 1.0, "tier2": 0.0, "tier3": 0.0, "tier4": 0.0 },
     *   "1": { "tier1": 0.8, "tier2": 0.2, "tier3": 0.0, "tier4": 0.0 },
     *   "default": { "tier1": 0.0, "tier2": 0.0, "tier3": 0.4, "tier4": 0.6 }
     * }
     */
    public static Map<String, ZoneSpawnChances> parseZoneSpawnChances(String json) {
        Map<String, ZoneSpawnChances> zoneChances = new LinkedHashMap<>();

        try {
            String zoneBlock = extractJsonObject(json, "zoneSpawnChances");
            if (zoneBlock == null) {
                LOGGER.info("No zoneSpawnChances block found in config, using defaults");
                return ZoneSpawnChances.generateDefaults();
            }

            // Parse each zone entry: "0": { ... }, "1": { ... }, "default": { ... }
            Pattern zonePattern = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{");
            Matcher matcher = zonePattern.matcher(zoneBlock);

            while (matcher.find()) {
                String zoneKey = matcher.group(1);
                int startPos = matcher.end() - 1;
                String zoneJson = extractObjectFromPosition(zoneBlock, startPos);

                if (zoneJson != null) {
                    ZoneSpawnChances chances = new ZoneSpawnChances();

                    Double tier1 = extractDoubleValue(zoneJson, "tier1");
                    if (tier1 != null) chances.setTier1(tier1);

                    Double tier2 = extractDoubleValue(zoneJson, "tier2");
                    if (tier2 != null) chances.setTier2(tier2);

                    Double tier3 = extractDoubleValue(zoneJson, "tier3");
                    if (tier3 != null) chances.setTier3(tier3);

                    Double tier4 = extractDoubleValue(zoneJson, "tier4");
                    if (tier4 != null) chances.setTier4(tier4);

                    zoneChances.put(zoneKey, chances);
                    LOGGER.info("Parsed zone spawn chances for zone '" + zoneKey + "': " + chances);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing zone spawn chances: " + e.getMessage());
            e.printStackTrace();
        }

        if (zoneChances.isEmpty()) {
            LOGGER.warning("No zone spawn chances parsed, falling back to defaults");
            return ZoneSpawnChances.generateDefaults();
        }

        // Ensure we have a "default" entry for unknown zones
        if (!zoneChances.containsKey("default")) {
            LOGGER.info("No 'default' zone config found, adding default for zone 4+");
            zoneChances.put("default", ZoneSpawnChances.getDefaultForZone(4));
        }

        return zoneChances;
    }

    // ========== Helper methods for JSON parsing ==========

    /**
     * Extract a nested JSON object by key
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

            return extractObjectFromPosition(json, braceIndex);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract a JSON array by key
     */
    private static String extractJsonArray(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            int bracketIndex = json.indexOf("[", colonIndex);
            if (bracketIndex == -1)
                return null;

            return extractArrayFromPosition(json, bracketIndex);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract a complete JSON object starting at a position (including braces)
     */
    private static String extractObjectFromPosition(String json, int startPos) {
        if (json.charAt(startPos) != '{')
            return null;

        int depth = 0;
        int endPos = startPos;
        boolean inString = false;

        for (int i = startPos; i < json.length(); i++) {
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

        return json.substring(startPos, endPos);
    }

    /**
     * Extract a complete JSON array starting at a position (including brackets)
     */
    private static String extractArrayFromPosition(String json, int startPos) {
        if (json.charAt(startPos) != '[')
            return null;

        int depth = 0;
        int endPos = startPos;
        boolean inString = false;

        for (int i = startPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '[')
                    depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        endPos = i + 1;
                        break;
                    }
                }
            }
        }

        return json.substring(startPos, endPos);
    }

    /**
     * Extract all objects from an array
     */
    private static List<String> extractArrayObjects(String arrayJson) {
        List<String> objects = new ArrayList<>();

        int i = 1; // Skip opening bracket
        while (i < arrayJson.length()) {
            char c = arrayJson.charAt(i);

            if (c == '{') {
                String obj = extractObjectFromPosition(arrayJson, i);
                if (obj != null) {
                    objects.add(obj);
                    i += obj.length();
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        return objects;
    }

    /**
     * Extract string values from a simple string array
     */
    private static List<String> extractStringArray(String arrayJson) {
        List<String> strings = new ArrayList<>();

        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(arrayJson);

        while (matcher.find()) {
            strings.add(matcher.group(1));
        }

        return strings;
    }

    /**
     * Extract an integer array by key
     */
    private static List<Integer> extractIntArray(String json, String key) {
        List<Integer> ints = new ArrayList<>();

        String arrayJson = extractJsonArray(json, key);
        if (arrayJson == null)
            return ints;

        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(arrayJson);

        while (matcher.find()) {
            try {
                ints.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Skip invalid numbers
            }
        }

        return ints;
    }

    /**
     * Extract a string value by key
     */
    private static String extractStringValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            int startQuote = json.indexOf("\"", colonIndex + 1);
            if (startQuote == -1)
                return null;

            int endQuote = json.indexOf("\"", startQuote + 1);
            if (endQuote == -1)
                return null;

            return json.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract an integer value by key
     */
    private static Integer extractIntValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            // Find the number after the colon
            int startIndex = colonIndex + 1;
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            StringBuilder num = new StringBuilder();
            while (startIndex < json.length()) {
                char c = json.charAt(startIndex);
                if (Character.isDigit(c) || c == '-') {
                    num.append(c);
                    startIndex++;
                } else {
                    break;
                }
            }

            if (num.length() > 0) {
                return Integer.parseInt(num.toString());
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return null;
    }

    /**
     * Extract a double value by key
     */
    private static Double extractDoubleValue(String json, String key) {
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

            StringBuilder num = new StringBuilder();
            while (startIndex < json.length()) {
                char c = json.charAt(startIndex);
                if (Character.isDigit(c) || c == '.' || c == '-') {
                    num.append(c);
                    startIndex++;
                } else {
                    break;
                }
            }

            if (num.length() > 0) {
                return Double.parseDouble(num.toString());
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return null;
    }

    /**
     * Extract a boolean value by key
     */
    private static Boolean extractBooleanValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1)
                return null;

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1)
                return null;

            String afterColon = json.substring(colonIndex + 1).trim();
            if (afterColon.startsWith("true"))
                return true;
            if (afterColon.startsWith("false"))
                return false;
        } catch (Exception e) {
            // Ignore parse errors
        }
        return null;
    }
}
