package com.cometmod.config;

import java.util.List;
import java.util.Map;

/**
 * JSON writer for generating config files.
 * Creates properly formatted JSON without external dependencies.
 */
public class ThemeConfigWriter {

    private static final String INDENT = "  ";

    /**
     * Generate complete config JSON with all settings
     */
    public static String generateFullConfig(
            int minDelaySeconds, int maxDelaySeconds, double spawnChance,
            double despawnTimeMinutes, int minSpawnDistance, int maxSpawnDistance,
            Map<String, ThemeConfig> themes, Map<Integer, TierSettings> tierSettings,
            Map<Integer, TierRewards> rewardSettings) {
        // Call overloaded method with defaults for new parameters
        return generateFullConfig(minDelaySeconds, maxDelaySeconds, spawnChance,
                despawnTimeMinutes, minSpawnDistance, maxSpawnDistance,
                true, false, // naturalSpawnsEnabled, globalComets
                themes, tierSettings, rewardSettings, null);
    }

    /**
     * Generate complete config JSON with all settings including zone spawn chances
     */
    public static String generateFullConfig(
            int minDelaySeconds, int maxDelaySeconds, double spawnChance,
            double despawnTimeMinutes, int minSpawnDistance, int maxSpawnDistance,
            Map<String, ThemeConfig> themes, Map<Integer, TierSettings> tierSettings,
            Map<Integer, TierRewards> rewardSettings, Map<String, ZoneSpawnChances> zoneSpawnChances) {
        // Call overloaded method with defaults for new parameters
        return generateFullConfig(minDelaySeconds, maxDelaySeconds, spawnChance,
                despawnTimeMinutes, minSpawnDistance, maxSpawnDistance,
                true, false, // naturalSpawnsEnabled, globalComets
                themes, tierSettings, rewardSettings, zoneSpawnChances);
    }

    /**
     * Generate complete config JSON with all settings including natural spawns toggle
     */
    public static String generateFullConfig(
            int minDelaySeconds, int maxDelaySeconds, double spawnChance,
            double despawnTimeMinutes, int minSpawnDistance, int maxSpawnDistance,
            boolean naturalSpawnsEnabled, boolean globalComets,
            Map<String, ThemeConfig> themes, Map<Integer, TierSettings> tierSettings,
            Map<Integer, TierRewards> rewardSettings, Map<String, ZoneSpawnChances> zoneSpawnChances) {

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Spawn settings section
        sb.append(INDENT).append("\"spawnSettings\": {\n");
        sb.append(INDENT).append(INDENT).append("\"naturalSpawnsEnabled\": ").append(naturalSpawnsEnabled).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"minDelaySeconds\": ").append(minDelaySeconds).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"maxDelaySeconds\": ").append(maxDelaySeconds).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"spawnChance\": ").append(spawnChance).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"despawnTimeMinutes\": ").append(despawnTimeMinutes).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"minSpawnDistance\": ").append(minSpawnDistance).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"maxSpawnDistance\": ").append(maxSpawnDistance).append(",\n");
        sb.append(INDENT).append(INDENT).append("\"globalComets\": ").append(globalComets).append("\n");
        sb.append(INDENT).append("},\n\n");

        // Zone spawn chances section
        writeZoneSpawnChances(sb, zoneSpawnChances);

        // Tier settings section
        sb.append(INDENT).append("\"tierSettings\": {\n");
        int tierCount = 0;
        for (Map.Entry<Integer, TierSettings> entry : tierSettings.entrySet()) {
            tierCount++;
            TierSettings ts = entry.getValue();
            sb.append(INDENT).append(INDENT).append("\"").append(entry.getKey()).append("\": {\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append("\"timeoutSeconds\": ")
                    .append(ts.getTimeoutSeconds()).append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append("\"minRadius\": ").append(ts.getMinRadius())
                    .append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append("\"maxRadius\": ").append(ts.getMaxRadius())
                    .append("\n");
            sb.append(INDENT).append(INDENT).append("}");
            if (tierCount < tierSettings.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(INDENT).append("},\n\n");

        // Reward settings section
        sb.append(INDENT).append("\"rewardSettings\": {\n");
        int rewardCount = 0;
        for (Map.Entry<Integer, TierRewards> entry : rewardSettings.entrySet()) {
            rewardCount++;
            TierRewards tr = entry.getValue();
            sb.append(INDENT).append(INDENT).append("\"").append(entry.getKey()).append("\": {\n");

            // Drops
            sb.append(INDENT).append(INDENT).append(INDENT).append("\"drops\": [\n");
            List<RewardEntry> drops = tr.getDrops();
            for (int i = 0; i < drops.size(); i++) {
                RewardEntry drop = drops.get(i);
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("{ \"id\": \"")
                        .append(drop.getId()).append("\", \"minCount\": ").append(drop.getMinCount())
                        .append(", \"maxCount\": ").append(drop.getMaxCount()).append(", \"chance\": ")
                        .append(drop.getChance()).append(", \"displayName\": \"")
                        .append(escapeString(drop.getDisplayName())).append("\" }");
                if (i < drops.size() - 1)
                    sb.append(",");
                sb.append("\n");
            }
            sb.append(INDENT).append(INDENT).append(INDENT).append("],\n");

            // Bonus Drops
            sb.append(INDENT).append(INDENT).append(INDENT).append("\"bonusDrops\": [\n");
            List<RewardEntry> bonusDrops = tr.getBonusDrops();
            for (int i = 0; i < bonusDrops.size(); i++) {
                RewardEntry bonus = bonusDrops.get(i);
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("{ \"id\": \"")
                        .append(bonus.getId()).append("\", \"minCount\": ").append(bonus.getMinCount())
                        .append(", \"maxCount\": ").append(bonus.getMaxCount()).append(", \"chance\": ")
                        .append(bonus.getChance()).append(", \"displayName\": \"")
                        .append(escapeString(bonus.getDisplayName())).append("\" }");
                if (i < bonusDrops.size() - 1)
                    sb.append(",");
                sb.append("\n");
            }
            sb.append(INDENT).append(INDENT).append(INDENT).append("]\n");

            sb.append(INDENT).append(INDENT).append("}");
            if (rewardCount < rewardSettings.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(INDENT).append("},\n\n");

        // Themes section
        sb.append(INDENT).append("\"themes\": {\n");
        int currentThemeCount = 0;
        for (Map.Entry<String, ThemeConfig> entry : themes.entrySet()) {
            currentThemeCount++;
            writeTheme(sb, entry.getKey(), entry.getValue(), currentThemeCount < themes.size());
        }
        sb.append(INDENT).append("}\n");

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Write a single theme to the StringBuilder
     */
    private static void writeTheme(StringBuilder sb, String id, ThemeConfig theme, boolean hasMore) {
        String i2 = INDENT + INDENT;
        String i3 = INDENT + INDENT + INDENT;
        String i4 = INDENT + INDENT + INDENT + INDENT;

        sb.append(i2).append("\"").append(id).append("\": {\n");
        sb.append(i3).append("\"displayName\": \"").append(escapeString(theme.getDisplayName())).append("\",\n");

        // Write tiers array
        sb.append(i3).append("\"tiers\": [");
        List<Integer> tiers = theme.getTiers();
        for (int i = 0; i < tiers.size(); i++) {
            sb.append(tiers.get(i));
            if (i < tiers.size() - 1)
                sb.append(", ");
        }
        sb.append("],\n");

        // Write mobs array
        // Write mobs array
        sb.append(i3).append("\"mobs\": [\n");
        List<MobEntry> mobs = theme.getMobs();
        for (int i = 0; i < mobs.size(); i++) {
            MobEntry mob = mobs.get(i);
            sb.append(i4).append("{ \"id\": \"").append(escapeString(mob.getId())).append("\", ");
            sb.append("\"count\": ").append(mob.getCount());

            // Write stats if present
            writeInlineStats(sb, mob.getMultipliers(), i4);

            sb.append(" }");
            if (i < mobs.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append(i3).append("],\n");

        // Write bosses array
        sb.append(i3).append("\"bosses\": [\n");
        List<BossEntry> bosses = theme.getBosses();
        for (int i = 0; i < bosses.size(); i++) {
            BossEntry boss = bosses.get(i);
            sb.append(i4).append("{ \"id\": \"").append(escapeString(boss.getId())).append("\"");

            // Write stats if present
            writeInlineStats(sb, boss.getMultipliers(), i4);

            sb.append(" }");
            if (i < bosses.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append(i3).append("]");

        sb.append("\n").append(i2).append("}");
        if (hasMore)
            sb.append(",");
        sb.append("\n");
    }

    private static void writeInlineStats(StringBuilder sb, Map<Integer, float[]> multipliers, String indent) {
        if (multipliers == null || multipliers.isEmpty())
            return;

        sb.append(", \"stats\": {");
        int count = 0;
        for (Map.Entry<Integer, float[]> entry : multipliers.entrySet()) {
            if (count > 0)
                sb.append(", ");
            float[] m = entry.getValue();
            sb.append("\"").append(entry.getKey()).append("\": { ")
                    .append("\"hp\": ").append(m[0]).append(", \"damage\": ").append(m[1])
                    .append(", \"scale\": ").append(m[2]).append(", \"speed\": ").append(m[3]).append(" }");
            count++;
        }
        sb.append(" }");
    }

    /**
     * Write zone spawn chances section to JSON
     */
    private static void writeZoneSpawnChances(StringBuilder sb, Map<String, ZoneSpawnChances> zoneSpawnChances) {
        // Use defaults if none provided
        if (zoneSpawnChances == null || zoneSpawnChances.isEmpty()) {
            zoneSpawnChances = ZoneSpawnChances.generateDefaults();
        }

        sb.append(INDENT).append("\"zoneSpawnChances\": {\n");
        int zoneCount = 0;
        for (Map.Entry<String, ZoneSpawnChances> entry : zoneSpawnChances.entrySet()) {
            zoneCount++;
            String zoneKey = entry.getKey();
            ZoneSpawnChances chances = entry.getValue();

            sb.append(INDENT).append(INDENT).append("\"").append(zoneKey).append("\": { ");
            sb.append("\"tier1\": ").append(chances.getTier1()).append(", ");
            sb.append("\"tier2\": ").append(chances.getTier2()).append(", ");
            sb.append("\"tier3\": ").append(chances.getTier3()).append(", ");
            sb.append("\"tier4\": ").append(chances.getTier4());
            sb.append(" }");

            if (zoneCount < zoneSpawnChances.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(INDENT).append("},\n\n");
    }

    /**
     * Escape special characters in JSON strings
     */
    private static String escapeString(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
