package com.cometmod;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages comet wave spawning, combat, and rewards.
 * 
 * CROSS-TIER THEME SYSTEM:
 * Each comet tier can spawn themes from adjacent tiers (+/- 1 tier).
 * The mob's tier suffix matches the COMET tier (not the theme's native tier),
 * so mobs are scaled appropriately.
 * 
 * - Tier 1 (Uncommon): Tier 1 themes + Tier 2 themes (nerfed to Tier 1 stats)
 * - Tier 2 (Rare): Tier 1 (buffed) + Tier 2 (native) + Tier 3 themes (nerfed)
 * - Tier 3 (Epic): Tier 2 (buffed) + Tier 3 (native) + Tier 4 themes (nerfed)
 * - Tier 4 (Legendary): Tier 3 (buffed) + Tier 4 (native)
 * 
 * Theme Native Tiers:
 * - Tier 1: Skeleton, Goblin, Spider
 * - Tier 2: Trork, Skeleton Sand, Sabertooth
 * - Tier 3: Outlander, Leopard
 * - Tier 4: Toad, Skeleton Burnt
 * 
 * The difficulty progression is: Uncommon (1) < Rare (2) < Epic (3) < Legendary
 * (4)
 */
public class CometWaveManager {
    private static final Logger LOGGER = Logger.getLogger("CometWaveManager");

    private com.hypixel.hytale.server.core.plugin.PluginBase plugin;

    // Track active comets and their wave states
    private final Map<Vector3i, CometState> activeComets = new ConcurrentHashMap<>();

    // Track tier for each comet block
    private final Map<Vector3i, CometTier> cometTiers = new ConcurrentHashMap<>();

    // Track owner UUID for each comet block (for marker visibility filtering)
    private final Map<Vector3i, java.util.UUID> cometOwners = new ConcurrentHashMap<>();

    public void setPlugin(com.hypixel.hytale.server.core.plugin.PluginBase plugin) {
        this.plugin = plugin;
    }

    private static final int WAVE_MOB_COUNT = 5;
    private static final Random RANDOM = new Random();

    // Bonus drop chance for lower-tier items
    private static final double BONUS_DROP_CHANCE_TIER2 = 0.35; // 35%
    private static final double BONUS_DROP_CHANCE_TIER3 = 0.30; // 30%
    private static final double BONUS_DROP_CHANCE_TIER4 = 0.25; // 25%

    // ========== THEME SYSTEM ==========
    // Base mob names (without tier suffix) - tier suffix is applied dynamically
    // based on comet tier

    // Tier 1 Native Themes (Skeleton, Goblin, Spider)
    private static final String[] THEME_SKELETON_MOBS = { "Skeleton_Soldier", "Skeleton_Archer", "Skeleton_Archmage" };
    private static final String[] THEME_GOBLIN_MOBS = { "Goblin_Scrapper", "Goblin_Miner", "Goblin_Lobber" };
    private static final String[] THEME_SPIDER_MOBS = { "Spider" };
    private static final String THEME_SPIDER_BOSS = "Spider_Broodmother";
    private static final String[] TIER1_BOSSES_BASE = { "Bear_Polar", "Wolf_Black" };

    // Tier 2 Native Themes (Trork, Skeleton Sand, Sabertooth)
    private static final String[] THEME_TRORK_MOBS = { "Trork_Warrior", "Trork_Hunter", "Trork_Mauler", "Trork_Shaman",
            "Trork_Brawler" };
    private static final String THEME_TRORK_BOSS = "Trork_Chieftain";
    private static final String[] THEME_SKELETON_SAND_MOBS = { "Skeleton_Sand_Archer", "Skeleton_Sand_Assassin",
            "Skeleton_Sand_Guard", "Skeleton_Sand_Mage", "Skeleton_Sand_Ranger", "Skeleton_Sand_Archmage" };
    private static final String[] THEME_SABERTOOTH_MOBS = { "Tiger_Sabertooth" };
    private static final String[] TIER2_BOSSES_BASE = { "Bear_Grizzly", "Skeleton_Burnt_Alchemist" };

    // Tier 3 Native Themes (Outlander, Leopard)
    private static final String[] THEME_OUTLANDER_MOBS = { "Outlander_Berserker", "Outlander_Cultist",
            "Outlander_Hunter", "Outlander_Stalker", "Outlander_Priest", "Outlander_Brute" };
    private static final String[] THEME_LEOPARD_MOBS = { "Leopard_Snow" };
    private static final String TIER3_BOSS_BASE = "Werewolf";

    // Tier 4 Native Themes (Toad, Skeleton Burnt)
    private static final String[] THEME_TOAD_MOBS = { "Toad_Rhino_Magma" };
    private static final String[] THEME_SKELETON_BURNT_MOBS = { "Skeleton_Burnt_Archer", "Skeleton_Burnt_Gunner",
            "Skeleton_Burnt_Knight", "Skeleton_Burnt_Lancer" };
    private static final String THEME_SKELETON_BURNT_BOSS = "Skeleton_Burnt_Praetorian";
    private static final String[] TIER4_BOSSES_BASE = { "Shadow_Knight", "Zombie_Aberrant" };

    // Void theme - available in all 4 comet tiers; wave 1: 2 Crawler + 2 Spectre;
    // boss: Spawn_Void
    private static final String[] THEME_VOID_MOBS = { "Crawler_Void", "Spectre_Void" };
    private static final String THEME_VOID_BOSS = "Spawn_Void";

    // Theme native tiers (which tier each theme naturally belongs to). Cross-tier:
    // comet can pick if |native - cometTier| <= 1.
    private static final int[] THEME_NATIVE_TIER = {
            1, 1, 1, 2, 2, 2, 3, 3, 4, 4, 2, // 0-10: Skeleton, Goblin, Spider, Trork, SkeletonSand, Sabertooth,
                                             // Outlander, Leopard, Toad, SkeletonBurnt, Void
            4, 4, 4, 4, 2, 4, 4 // 11-17: Ice, BurntLegendary, Lava, Earth, UndeadRare, UndeadLegendary, Zombie
    };

    // Theme identifiers
    private static final int THEME_SKELETON = 0;
    private static final int THEME_GOBLIN = 1;
    private static final int THEME_SPIDER = 2;
    private static final int THEME_TRORK = 3;
    private static final int THEME_SKELETON_SAND = 4;
    private static final int THEME_SABERTOOTH = 5;
    private static final int THEME_OUTLANDER = 6;
    private static final int THEME_LEOPARD = 7;
    private static final int THEME_TOAD = 8;
    private static final int THEME_SKELETON_BURNT = 9;
    private static final int THEME_VOID = 10;
    private static final int THEME_ICE = 11;
    private static final int THEME_BURNT_LEGENDARY = 12;
    private static final int THEME_LAVA = 13;
    private static final int THEME_EARTH = 14;
    private static final int THEME_UNDEAD_RARE = 15;
    private static final int THEME_UNDEAD_LEGENDARY = 16;
    private static final int THEME_ZOMBIE = 17;

    // Theme display names
    private static final String[] THEME_NAMES = {
            "Skeleton Horde", "Goblin Gang", "Spider Swarm", "Trork Warband",
            "Sand Skeleton Legion", "Sabertooth Pack", "Outlander Cult", "Snow Leopard Pride",
            "Magma Toads", "Burnt Legion", "Voidspawn", "Legendary Ice",
            "Burnt Legion", "Legendary Lava", "Legendary Earth", "Rare Undead",
            "Legendary Undead", "Zombie Aberration"
    };

    // Track current theme ID for boss selection (string-based for config
    // compatibility)
    private final Map<Vector3i, String> cometThemes = new ConcurrentHashMap<>();

    // Track forced theme ID for a comet (set by spawn command)
    private final Map<Vector3i, String> forcedThemes = new ConcurrentHashMap<>();

    // Tier timeouts in milliseconds (per wave: adds + boss)
    private static final long TIER1_TIMEOUT = 90000; // 90 seconds (Uncommon)
    private static final long TIER2_TIMEOUT = 150000; // 150 seconds / 2.5 min (Rare)
    private static final long TIER3_TIMEOUT = 180000; // 180 seconds / 3 min (Epic)
    private static final long TIER4_TIMEOUT = 240000; // 240 seconds / 4 min (Legendary)

    // Spawn radii per tier
    private static final double[] TIER_MIN_RADIUS = { 3.0, 4.0, 5.0, 6.0 };
    private static final double[] TIER_MAX_RADIUS = { 5.0, 6.0, 7.0, 8.0 };

    // Max ranged mobs per wave (applies to ALL tiers)
    private static final int MAX_RANGED_PER_WAVE = 1;

    public enum CometState {
        UNTOUCHED,
        WAVE_ACTIVE,
        COMPLETED
    }

    // Get comet state from block state (persists across relogs)
    public CometState getCometState(Vector3i blockPos) {
        // Check memory first
        CometState memoryState = activeComets.get(blockPos);
        if (memoryState != null) {
            return memoryState;
        }
        return CometState.UNTOUCHED;
    }

    /**
     * Get all active comets for map marker display
     * 
     * @return Map of comet positions to their states
     */
    public Map<Vector3i, CometState> getActiveComets() {
        return new HashMap<>(activeComets); // Return a copy for thread safety
    }

    /**
     * Get all comet tiers for map marker display
     * 
     * @return Map of comet positions to their tiers
     */
    public Map<Vector3i, CometTier> getCometTiers() {
        return new HashMap<>(cometTiers); // Return a copy for thread safety
    }

    /**
     * Get all comet owners for map marker filtering
     *
     * @return Map of comet positions to their owner UUIDs
     */
    public Map<Vector3i, java.util.UUID> getCometOwners() {
        return new HashMap<>(cometOwners); // Return a copy for thread safety
    }

    /**
     * Check if there's an active comet near the given position
     *
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param z        Z coordinate
     * @param distance Maximum distance to check
     * @return true if there's an active comet within distance
     */
    public boolean hasActiveCometNear(int x, int y, int z, int distance) {
        for (Vector3i pos : activeComets.keySet()) {
            double dx = pos.x - x;
            double dy = pos.y - y;
            double dz = pos.z - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= distance) {
                return true;
            }
        }
        // Also check cometTiers for registered but not yet activated comets
        for (Vector3i pos : cometTiers.keySet()) {
            double dx = pos.x - x;
            double dy = pos.y - y;
            double dz = pos.z - z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= distance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the owner UUID for a specific comet
     * 
     * @param blockPos The comet block position
     * @return The owner UUID, or null if not found
     */
    public java.util.UUID getCometOwner(Vector3i blockPos) {
        return cometOwners.get(blockPos);
    }

    private static class WaveData {
        final List<Ref<EntityStore>> spawnedMobs = new ArrayList<>();
        final Vector3i blockPos;
        final Ref<EntityStore> playerRef;
        long startTime; // Track when wave started for timeout (not final - needs to be reset for each wave)
        long lastTimerUpdate = 0; // Track last time timer was updated (to update every 5 seconds)
        int initialSpawnCount = 0; // Track how many mobs were actually spawned
        int remainingCount = WAVE_MOB_COUNT;
        int previousRemainingCount = WAVE_MOB_COUNT; // Track previous count to detect changes
        int currentWave = 1; // Track wave number (1-based for display, internally converted from 0-based index)
        int currentWaveIndex = 0; // 0-based wave index for multi-wave support
        int totalWaveCount = 2; // Total waves in this encounter (default 2: 1 normal + 1 boss)
        String themeName = "Unknown"; // Display name of the current theme

        WaveData(Vector3i blockPos, Ref<EntityStore> playerRef) {
            this.blockPos = blockPos;
            this.playerRef = playerRef;
            this.startTime = System.currentTimeMillis();
            this.lastTimerUpdate = this.startTime;
        }

        boolean hasMoreWaves() {
            return currentWaveIndex < totalWaveCount - 1;
        }

        void advanceToNextWave() {
            currentWaveIndex++;
            currentWave = currentWaveIndex + 1;
            startTime = System.currentTimeMillis();
            lastTimerUpdate = startTime;
            spawnedMobs.clear();
            initialSpawnCount = 0;
        }
    }

    private static class FailedSpawnInfo {
        final String npcType;
        final Vector3f rotation;
        final boolean isRanged;

        FailedSpawnInfo(String n, Vector3f r, boolean ir) {
            npcType = n;
            rotation = r;
            isRanged = ir;
        }
    }

    private static boolean isRangedMob(String npcType) {
        if (npcType == null)
            return false;
        String[] r = { "Archer", "Archmage", "Lobber", "Shaman", "Mage", "Ranger", "Hunter", "Stalker", "Priest",
                "Gunner", "Alchemist" };
        for (String x : r)
            if (npcType.contains(x))
                return true;
        return false;
    }

    private final Map<Vector3i, WaveData> activeWaves = new ConcurrentHashMap<>();

    /**
     * Check for wave timeouts and destroy expired comets
     * This should be called periodically (every 5 seconds) from the plugin
     * NOTE: This runs on the scheduler thread, so we need to execute world
     * operations on WorldThread
     */
    public void checkTimeouts() {
        // Check all active waves for timeout
        long currentTime = System.currentTimeMillis();
        java.util.List<Vector3i> timedOutWaves = new java.util.ArrayList<>();

        for (Map.Entry<Vector3i, WaveData> entry : activeWaves.entrySet()) {
            WaveData waveData = entry.getValue();
            long elapsedTime = currentTime - waveData.startTime;

            // Get tier-specific timeout
            CometTier tier = cometTiers.getOrDefault(entry.getKey(), CometTier.UNCOMMON);
            long tierTimeout = getTierTimeout(tier);

            if (elapsedTime >= tierTimeout) {
                LOGGER.info("[checkTimeouts] TIMEOUT for wave at " + entry.getKey() +
                        " (elapsed=" + (elapsedTime / 1000) + "s)");
                timedOutWaves.add(entry.getKey());
            }
        }

        // Destroy timed out waves - must execute on WorldThread
        for (Vector3i blockPos : timedOutWaves) {
            WaveData waveData = activeWaves.get(blockPos);
            if (waveData == null)
                continue;

            // Try to find a valid store to execute the cleanup
            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = null;

            if (waveData.playerRef != null && waveData.playerRef.isValid()) {
                store = waveData.playerRef.getStore();
            } else {
                // Player dead/gone, try to use a mob ref
                for (Ref<EntityStore> mobRef : waveData.spawnedMobs) {
                    if (mobRef != null && mobRef.isValid()) {
                        store = mobRef.getStore();
                        break;
                    }
                }
            }

            if (store != null) {
                // Get world and execute on WorldThread
                try {
                    com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                            .getExternalData()).getWorld();

                    final Store<EntityStore> finalStore = store;
                    final WaveData finalWaveData = waveData;

                    world.execute(() -> {
                        destroyCometOnTimeout(finalStore, finalWaveData);
                    });
                } catch (Exception e) {
                    LOGGER.warning("Error executing cleanup for timed out wave at " + blockPos + ": " + e.getMessage());
                }
            } else {
                // Critical failure: No valid store found to clean up wave.
                // Just remove it from active waves to prevent infinite loops,
                // though the block and mobs might linger.
                activeWaves.remove(blockPos);
                LOGGER.warning("Could not find valid store to clean up orphaned wave at " + blockPos);
            }
        }
    }

    public void handleCometActivation(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3i blockPos) {
        // Check block state first to see if it's already completed (persists across
        // relogs)
        com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                .getExternalData()).getWorld();
        com.hypixel.hytale.server.core.universe.world.meta.BlockState blockState = world.getState(blockPos.x,
                blockPos.y, blockPos.z, false);

        // If block is already an ItemContainerState with droplist, it's completed
        if (blockState instanceof com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState) {
            com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState containerState = (com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState) blockState;
            String droplist = containerState.getDroplist();
            if (droplist != null && droplist.startsWith("Comet_Rewards")) {
                // Already completed - open container or destroy if empty
                LOGGER.info("Comet at " + blockPos + " already completed (persisted state)");

                // Try to determine tier from droplist name
                CometTier tier = CometTier.UNCOMMON;
                for (CometTier t : CometTier.values()) {
                    if (droplist.equals(t.getLootTableName())) {
                        tier = t;
                        break;
                    }
                }
                cometTiers.put(blockPos, tier);

                // Don't check if empty here - the droplist might not have populated yet
                // Just open the container - it will populate when opened
                // We'll check if empty when the window closes
                LOGGER.info("Comet already completed, will open container via interaction");
                return;
            }
        }

        // Try to determine tier from block type
        CometTier tier = determineTierFromBlock(world, blockPos);
        if (tier == null) {
            tier = CometTier.UNCOMMON; // Default
        }
        cometTiers.put(blockPos, tier);

        // Check if this comet is already active (in memory)
        CometState state = activeComets.getOrDefault(blockPos, CometState.UNTOUCHED);

        if (state == CometState.WAVE_ACTIVE) {
            // Wave already active, don't spawn again
            LOGGER.info("Comet at " + blockPos + " already has active wave");
            return;
        }

        if (state == CometState.COMPLETED) {
            // Already completed, allow opening chest
            LOGGER.info("Comet at " + blockPos + " already completed (memory state)");
            // The interaction will handle opening the container
            return;
        }

        // Start a new wave
        activeComets.put(blockPos, CometState.WAVE_ACTIVE);

        LOGGER.info("Starting wave for comet at " + blockPos + " (tier: " + tier.getName() + ") - 3 second countdown");

        final com.hypixel.hytale.server.core.universe.world.World worldForCountdown = world;
        final Store<EntityStore> storeFinal = store;
        final Ref<EntityStore> playerRefFinal = playerRef;
        final Vector3i blockPosFinal = blockPos;
        final CometTier tierFinal = tier;

        // 3 second countdown: show "3", "2", "1" then spawn wave
        for (int i = 3; i >= 1; i--) {
            final int count = i;
            com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                worldForCountdown.execute(() -> {
                    if (!playerRefFinal.isValid())
                        return;
                    PlayerRef pr = storeFinal.getComponent(playerRefFinal, PlayerRef.getComponentType());
                    if (pr == null)
                        return;
                    EventTitleUtil.hideEventTitleFromPlayer(pr, 0.0F);
                    EventTitleUtil.showEventTitleToPlayer(
                            pr,
                            Message.raw(String.valueOf(count)),
                            Message.raw(""),
                            true,
                            null,
                            1.0F,
                            0.1F,
                            0.1F);
                });
            }, 3L - count, TimeUnit.SECONDS);
        }

        // After 3 seconds, spawn the wave
        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            worldForCountdown.execute(() -> {
                if (!playerRefFinal.isValid()) {
                    activeComets.remove(blockPosFinal);
                    return;
                }
                spawnWave(storeFinal, playerRefFinal, blockPosFinal, tierFinal);
            });
        }, 3L, TimeUnit.SECONDS);
    }

    /**
     * Determine tier from block type at position
     */
    private CometTier determineTierFromBlock(com.hypixel.hytale.server.core.universe.world.World world,
            Vector3i blockPos) {
        try {
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = world
                    .getBlockType(blockPos.x, blockPos.y, blockPos.z);
            if (blockType != null) {
                String blockId = blockType.getId();
                for (CometTier tier : CometTier.values()) {
                    if (blockId.equals(tier.getBlockId("Comet_Stone"))) {
                        return tier;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error determining tier from block: " + e.getMessage());
        }
        return CometTier.UNCOMMON; // Default
    }

    /**
     * Helper to spawn an NPC and register it for stat modifiers if the theme has
     * them configured.
     * This wraps the standard spawnNPC call and adds stat modifier registration.
     * 
     * @param store     The entity store
     * @param npcPlugin The NPC plugin
     * @param npcType   The full NPC type string (with tier suffix)
     * @param baseMobId The base mob ID (without tier suffix) for config lookup
     * @param spawnPos  The spawn position
     * @param rotation  The rotation
     * @param themeId   The theme ID for stat multiplier lookup
     * @param tier      The comet tier
     * @param isBoss    Whether this is a boss spawn
     * @return The spawn result pair, or null if failed
     */
    private Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> spawnCometNPC(
            Store<EntityStore> store,
            NPCPlugin npcPlugin,
            String npcType,
            String baseMobId,
            Vector3d spawnPos,
            Vector3f rotation,
            String themeId,
            CometTier tier,
            boolean isBoss) {

        try {
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result = npcPlugin
                    .spawnNPC(store, npcType, null, spawnPos, rotation);

            if (result != null && result.first() != null) {
                // Try to register stat modifiers if the theme has them
                try {
                    float[] multipliers = null;
                    if (isBoss) {
                        multipliers = WaveThemeProvider.getBossStatMultipliers(themeId, tier, baseMobId);
                    } else {
                        multipliers = WaveThemeProvider.getMobStatMultipliers(themeId, tier, baseMobId);
                    }

                    if (multipliers != null && multipliers.length >= 4) {
                        float hpMult = multipliers[0];
                        float damageMult = multipliers[1];
                        float scaleMult = multipliers[2];
                        float speedMult = multipliers[3];

                        // Get the UUID from the spawned entity
                        com.hypixel.hytale.server.core.entity.UUIDComponent uuidComp = store.getComponent(
                                result.first(),
                                com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());

                        if (uuidComp != null) {
                            UUID npcUUID = uuidComp.getUuid();
                            // Call directly to apply modifiers immediately (fixes timing issue)
                            CometStatModifierSystem.applyModifiers(store, result.first(), hpMult, damageMult,
                                    scaleMult, speedMult);
                            LOGGER.info("[CometWave] Applied stat modifiers for " + npcType +
                                    " (UUID: " + npcUUID + "): HP=" + hpMult + "x, Dmg=" + damageMult + "x, Scale="
                                    + scaleMult + "x, Speed=" + speedMult + "x");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning(
                            "[CometWave] Could not register stat modifiers for " + npcType + ": " + e.getMessage());
                }
            }

            return result;
        } catch (Exception e) {
            LOGGER.warning("[CometWave] Exception spawning NPC " + npcType + ": " + e.getMessage());
            return null;
        }
    }

    private void spawnWave(Store<EntityStore> store, Ref<EntityStore> playerRef, Vector3i blockPos, CometTier tier) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.warning("NPCPlugin not available!");
            return;
        }

        Vector3d centerPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 1, blockPos.z + 0.5);
        WaveData waveData = new WaveData(blockPos, playerRef);
        activeWaves.put(blockPos, waveData);

        // Select theme and get mob list based on tier (config-based system)
        String themeId;
        if (forcedThemes.containsKey(blockPos)) {
            themeId = forcedThemes.get(blockPos);
            LOGGER.info("Using forced theme for comet at " + blockPos + ": " + WaveThemeProvider.getThemeName(themeId));
        } else {
            themeId = WaveThemeProvider.selectTheme(tier);
        }

        // Fallback to legacy theme if config-based selection fails
        if (themeId == null) {
            LOGGER.warning("Config-based theme selection failed, using legacy fallback");
            int legacyTheme = selectThemeLegacy(tier);
            themeId = getLegacyThemeId(legacyTheme);
        }

        cometThemes.put(blockPos, themeId);

        // Initialize wave count from theme config (multi-wave support)
        waveData.totalWaveCount = WaveThemeProvider.getWaveCount(themeId);
        waveData.currentWaveIndex = 0;
        waveData.currentWave = 1;
        LOGGER.info("Theme '" + themeId + "' has " + waveData.totalWaveCount + " waves (" +
                WaveThemeProvider.getNormalWaveCount(themeId) + " normal, " +
                WaveThemeProvider.getBossWaveCount(themeId) + " boss)");

        // Get mob list for wave 0 (first wave)
        String[] mobList = WaveThemeProvider.getMobListForWave(tier, themeId, 0);

        LOGGER.info("[DEBUG] getMobListForTheme returned: " + (mobList == null ? "null" : mobList.length + " mobs"));
        if (mobList != null && mobList.length > 0) {
            LOGGER.info("[DEBUG] First 3 mobs: " + String.join(", ", java.util.Arrays.copyOf(mobList, Math.min(3, mobList.length))));
        }

        // Fallback to legacy mob list if config-based returns empty
        if (mobList == null || mobList.length == 0) {
            LOGGER.warning("[DEBUG] Config mob list empty, falling back to legacy system for theme: " + themeId);
            int legacyTheme = getLegacyThemeIndex(themeId);
            if (legacyTheme >= 0) {
                mobList = getMobListForThemeLegacy(tier, legacyTheme);
                LOGGER.info("[DEBUG] Legacy system returned " + (mobList == null ? "null" : mobList.length + " mobs"));
            }
        }

        // Store theme name for display
        waveData.themeName = WaveThemeProvider.getThemeName(themeId);
        LOGGER.info("Selected theme: " + waveData.themeName + " (ID: " + themeId + ") for tier " + tier.getName());

        if (mobList == null || mobList.length == 0) {
            LOGGER.warning("No mobs available for tier " + tier.getName() + " theme " + themeId);
            return;
        }

        // Get tier-specific spawn radius from config
        double[] radiusRange = WaveThemeProvider.getSpawnRadius(tier);
        double minRadius = radiusRange[0];
        double maxRadius = radiusRange[1];

        // Use actual mobList length for tier-specific counts
        int waveMobCount = mobList.length;

        // Shuffle mobList to randomize spawn order while maintaining exact counts
        java.util.List<String> mobListShuffled = new java.util.ArrayList<>(java.util.Arrays.asList(mobList));
        java.util.Collections.shuffle(mobListShuffled, RANDOM);
        mobList = mobListShuffled.toArray(new String[0]);

        // Get legacy theme integer for backwards compatibility with fixed composition
        // logic
        int theme = getLegacyThemeIndex(themeId);

        // Track ranged mobs - max 1 ranged per wave for ALL tiers
        int rangedCount = 0;
        // All ranged mob identifiers across all tiers
        String[] rangedMobs = {
                "Archer", "Archmage", "Lobber", "Shaman", "Mage", "Ranger",
                "Hunter", "Stalker", "Priest", "Gunner", "Alchemist"
        };

        com.hypixel.hytale.server.core.universe.world.World world = null;
        try {
            world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData())
                    .getWorld();
        } catch (Exception e) {
            LOGGER.warning("Could not get World for mob spawn validation: " + e.getMessage());
        }

        List<Vector3d> successPositions = new ArrayList<>();
        List<FailedSpawnInfo> failedSpawns = new ArrayList<>();

        // Spawn mobs in a circle around the comet
        String[] fb = getFixedCompBases(tier, theme);
        if (fb != null) {
            int[] fc = getFixedCompCounts(tier, theme);
            // Outlander Legendary: 30% of waves get 1 Priest (rare spawn)
            if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY && RANDOM.nextDouble() < 0.3)
                fc = new int[] { 3, 1, 2, 1 };
            int idx = 0;
            for (int i = 0; i < fb.length; i++) {
                for (int j = 0; j < fc[i]; j++) {
                    double angle = (2.0 * Math.PI * idx) / waveMobCount;
                    double radius = minRadius + (Math.random() * (maxRadius - minRadius));
                    Vector3d spawnPos = new Vector3d(centerPos.x + Math.cos(angle) * radius, centerPos.y,
                            centerPos.z + Math.sin(angle) * radius);
                    Vector3d toSpawn = spawnPos;
                    if (world != null) {
                        Vector3d v = CometSpawnUtil.findValidMobSpawn(world, spawnPos, 11);
                        if (v != null)
                            toSpawn = v;
                        else {
                            String npc = applyTierSuffix(fb[i], tier);
                            failedSpawns.add(new FailedSpawnInfo(npc,
                                    new Vector3f(0.0f, (float) (angle + Math.PI), 0.0f), isRangedMob(npc)));
                            LOGGER.info("No valid mob spawn at " + spawnPos + ", will retry near success: " + npc);
                            idx++;
                            continue;
                        }
                    }
                    Vector3f rot = new Vector3f(0.0f, (float) (angle + Math.PI), 0.0f);
                    String npcType = applyTierSuffix(fb[i], tier);
                    String baseMobId = fb[i]; // Base mob ID for config lookup
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> res = spawnCometNPC(
                            store, npcPlugin, npcType, baseMobId, toSpawn, rot, themeId, tier, false);
                    if (res != null && res.first() != null) {
                        waveData.spawnedMobs.add(res.first());
                        successPositions.add(toSpawn);
                        LOGGER.info("Spawned " + npcType + " at " + toSpawn);
                    } else {
                        LOGGER.warning("Failed to spawn NPC: " + npcType);
                    }
                    idx++;
                }
            }
        } else {
            for (int i = 0; i < waveMobCount; i++) {
                double angle = (2.0 * Math.PI * i) / waveMobCount;
                double radius = minRadius + (Math.random() * (maxRadius - minRadius));
                double x = centerPos.x + Math.cos(angle) * radius;
                double y = centerPos.y;
                double z = centerPos.z + Math.sin(angle) * radius;

                Vector3d spawnPos = new Vector3d(x, y, z);
                Vector3f rotation = new Vector3f(0.0f, (float) (angle + Math.PI), 0.0f);

                String npcType;
                if (tier == CometTier.UNCOMMON && theme == THEME_SKELETON) {
                    if (i < 3)
                        npcType = applyTierSuffix("Skeleton_Soldier", tier);
                    else
                        npcType = applyTierSuffix(RANDOM.nextBoolean() ? "Skeleton_Archer" : "Skeleton_Archmage", tier);
                } else if (theme == THEME_OUTLANDER && tier == CometTier.EPIC && rangedCount < MAX_RANGED_PER_WAVE
                        && RANDOM.nextDouble() < 0.05) {
                    // Outlander Epic: Priest is a rare spawn (5% per slot)
                    npcType = applyTierSuffix("Outlander_Priest", tier);
                } else {
                    // Use shuffled array index to maintain exact counts from config
                    npcType = mobList[i];
                    if (rangedCount >= MAX_RANGED_PER_WAVE) {
                        String nonRangedType = null;
                        for (String mob : mobList) {
                            boolean isRanged = false;
                            for (String ranged : rangedMobs) {
                                if (mob.contains(ranged)) {
                                    isRanged = true;
                                    break;
                                }
                            }
                            if (!isRanged) {
                                nonRangedType = mob;
                                break;
                            }
                        }
                        if (nonRangedType != null)
                            npcType = nonRangedType;
                    }
                    for (String ranged : rangedMobs) {
                        if (npcType.contains(ranged)) {
                            rangedCount++;
                            break;
                        }
                    }
                }

                Vector3d toSpawn = spawnPos;
                if (world != null) {
                    Vector3d v = CometSpawnUtil.findValidMobSpawn(world, spawnPos, 11);
                    if (v != null)
                        toSpawn = v;
                    else {
                        failedSpawns.add(new FailedSpawnInfo(npcType, rotation, isRangedMob(npcType)));
                        LOGGER.info("No valid mob spawn at " + spawnPos + ", will retry near success: " + npcType);
                        continue;
                    }
                }

                // Mob IDs are base IDs without tier suffixes
                Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result = spawnCometNPC(
                        store, npcPlugin, npcType, npcType, toSpawn, rotation, themeId, tier, false);
                if (result != null && result.first() != null) {
                    waveData.spawnedMobs.add(result.first());
                    successPositions.add(toSpawn);
                    LOGGER.info("Spawned " + npcType + " at " + toSpawn);
                } else {
                    LOGGER.warning("Failed to spawn NPC: " + npcType);
                }
            }
        }

        // Retry failed spawns near a successful one
        if (!failedSpawns.isEmpty() && !successPositions.isEmpty() && world != null) {
            for (FailedSpawnInfo f : failedSpawns) {
                Vector3d base = successPositions.get(RANDOM.nextInt(successPositions.size()));
                double dx = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                double dz = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                Vector3d newPref = new Vector3d(base.x + dx, base.y, base.z + dz);
                Vector3d retryPos = CometSpawnUtil.findValidMobSpawn(world, newPref, 11);
                if (retryPos != null) {
                    // Mob IDs are base IDs without tier suffixes
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> res = spawnCometNPC(
                            store, npcPlugin, f.npcType, f.npcType, retryPos, f.rotation, themeId, tier, false);
                    if (res != null && res.first() != null) {
                        waveData.spawnedMobs.add(res.first());
                        successPositions.add(retryPos);
                        LOGGER.info("Spawned " + f.npcType + " at " + retryPos + " (retry near success)");
                    }
                }
            }
        }

        // Store the actual number of mobs that were successfully spawned
        waveData.initialSpawnCount = waveData.spawnedMobs.size();
        waveData.previousRemainingCount = waveData.initialSpawnCount;
        LOGGER.info("Successfully spawned " + waveData.initialSpawnCount + " mobs out of " + waveMobCount
                + " attempted for tier " + tier.getName() + " theme " + themeId);

        // Start tracking and updating UI
        waveData.lastTimerUpdate = 0;
        updateWaveCountdown(store, playerRef, waveData);
    }

    /**
     * Get the display name for a theme
     */
    private String getThemeName(int theme) {
        if (theme >= 0 && theme < THEME_NAMES.length) {
            return THEME_NAMES[theme];
        }
        return "Unknown";
    }

    /**
     * Select a random theme for the given comet tier.
     * Cross-tier system: Each tier can access themes from adjacent tiers (+/- 1).
     * - Tier 1: Tier 1 themes + Tier 2 themes (nerfed)
     * - Tier 2: Tier 1 themes (buffed) + Tier 2 themes + Tier 3 themes (nerfed)
     * - Tier 3: Tier 2 themes (buffed) + Tier 3 themes + Tier 4 themes (nerfed)
     * - Tier 4: Tier 3 themes (buffed) + Tier 4 themes
     */
    private int selectTheme(CometTier tier) {
        List<Integer> availableThemes = new ArrayList<>();
        int cometTierNum = getTierIndex(tier) + 1; // 1-4

        // Add all themes within +/- 1 tier of the comet tier. Void: Uncommon/Rare/Epic
        // only (excluded at Legendary)
        for (int theme = 0; theme < THEME_NATIVE_TIER.length; theme++) {
            int themeNativeTier = THEME_NATIVE_TIER[theme];
            if (theme == THEME_VOID) {
                if (cometTierNum < 4)
                    availableThemes.add(theme); // Void not at Legendary
            } else if (Math.abs(themeNativeTier - cometTierNum) <= 1) {
                availableThemes.add(theme);
            }
        }

        if (availableThemes.isEmpty()) {
            // Fallback - should never happen
            LOGGER.warning("No available themes for tier " + tier.getName() + ", defaulting to Skeleton");
            return THEME_SKELETON;
        }

        return availableThemes.get(RANDOM.nextInt(availableThemes.size()));
    }

    /**
     * Get the tier suffix string (e.g., "_Tier1", "_Tier2", etc.)
     */
    private String getTierSuffix(CometTier tier) {
        switch (tier) {
            case UNCOMMON:
                return "_Tier1";
            case RARE:
                return "_Tier2";
            case EPIC:
                return "_Tier3";
            case LEGENDARY:
                return "_Tier4";
            default:
                return "_Tier1";
        }
    }

    /**
     * Apply tier suffix to a base mob name
     */
    private String applyTierSuffix(String baseName, CometTier tier) {
        // Now using base NPCs with dynamic stat multipliers instead of tiered JSON
        // files.
        return baseName;
    }

    /**
     * Get mob list for a theme, with tier suffix applied based on comet tier.
     * This enables cross-tier spawning (e.g., Sabertooth in Tier 1 becomes
     * Tiger_Sabertooth_Tier1)
     */
    private String[] getMobListForTheme(CometTier tier, int theme) {
        String[] baseMobs = getBaseMobsForTheme(theme, tier);
        if (baseMobs == null) {
            return null;
        }

        // Apply the comet's tier suffix to all base mob names
        String[] tieredMobs = new String[baseMobs.length];
        for (int i = 0; i < baseMobs.length; i++) {
            tieredMobs[i] = applyTierSuffix(baseMobs[i], tier);
        }
        return tieredMobs;
    }

    /**
     * Get base mob names (without tier suffix) for a theme.
     * Outlander: Priest is excluded in Tier 2 (Rare) and is a rare spawn in Tier 3
     * (Epic)
     * so it is excluded from the main pool there too.
     */
    private String[] getBaseMobsForTheme(int theme, CometTier tier) {
        switch (theme) {
            case THEME_SKELETON:
                return THEME_SKELETON_MOBS;
            case THEME_GOBLIN:
                return THEME_GOBLIN_MOBS;
            case THEME_SPIDER:
                return THEME_SPIDER_MOBS;
            case THEME_TRORK:
                return THEME_TRORK_MOBS;
            case THEME_SKELETON_SAND:
                return THEME_SKELETON_SAND_MOBS;
            case THEME_SABERTOOTH:
                return THEME_SABERTOOTH_MOBS;
            case THEME_OUTLANDER: {
                // Priest: not in Tier 2 (Rare); in Epic it is a rare spawn handled in spawnWave
                if (tier == CometTier.RARE || tier == CometTier.EPIC) {
                    return java.util.Arrays.stream(THEME_OUTLANDER_MOBS)
                            .filter(m -> !"Outlander_Priest".equals(m))
                            .toArray(String[]::new);
                }
                return THEME_OUTLANDER_MOBS;
            }
            case THEME_LEOPARD:
                return THEME_LEOPARD_MOBS;
            case THEME_TOAD:
                return THEME_TOAD_MOBS;
            case THEME_SKELETON_BURNT:
                return THEME_SKELETON_BURNT_MOBS;
            case THEME_VOID:
                return THEME_VOID_MOBS;
            case THEME_ICE:
                return new String[] { "Yeti", "Bear_Polar", "Golem_Crystal_Frost", "Leopard_Snow" };
            case THEME_BURNT_LEGENDARY:
                return new String[] { "Skeleton_Burnt_Archer", "Skeleton_Burnt_Alchemist", "Skeleton_Burnt_Lancer",
                        "Skeleton_Burnt_Knight" };
            case THEME_LAVA:
                return new String[] { "Emberwulf", "Golem_Firesteel", "Spirit_Ember" };
            case THEME_EARTH:
                return new String[] { "Golem_Crystal_Earth", "Bear_Grizzly", "Hyena" };
            case THEME_UNDEAD_RARE:
                return new String[] { "Pig_Undead", "Cow_Undead", "Chicken_Undead" };
            case THEME_UNDEAD_LEGENDARY:
                return new String[] { "Pig_Undead", "Cow_Undead", "Chicken_Undead", "Hound_Bleached" };
            case THEME_ZOMBIE:
                return new String[] { "Zombie_Aberrant_Small" };
            default:
                return null;
        }
    }

    /**
     * Fixed-composition themes: return base names; null = use random from
     * getBaseMobsForTheme.
     */
    private String[] getFixedCompBases(CometTier tier, int theme) {
        if (theme == THEME_VOID) {
            return (tier == CometTier.EPIC) ? new String[] { "Eye_Void", "Spawn_Void" }
                    : new String[] { "Crawler_Void", "Spectre_Void" };
        }
        if (theme == THEME_ICE)
            return new String[] { "Yeti", "Bear_Polar", "Golem_Crystal_Frost", "Leopard_Snow" };
        if (theme == THEME_BURNT_LEGENDARY)
            return new String[] { "Skeleton_Burnt_Archer", "Skeleton_Burnt_Alchemist", "Skeleton_Burnt_Lancer",
                    "Skeleton_Burnt_Knight" };
        if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY)
            return new String[] { "Outlander_Berserker", "Outlander_Brute", "Outlander_Hunter", "Outlander_Priest" };
        if (theme == THEME_LAVA)
            return new String[] { "Emberwulf", "Golem_Firesteel", "Spirit_Ember" };
        if (theme == THEME_EARTH)
            return new String[] { "Golem_Crystal_Earth", "Bear_Grizzly", "Hyena" };
        if (theme == THEME_UNDEAD_RARE)
            return new String[] { "Pig_Undead", "Cow_Undead", "Chicken_Undead" };
        if (theme == THEME_UNDEAD_LEGENDARY)
            return new String[] { "Pig_Undead", "Cow_Undead", "Chicken_Undead", "Hound_Bleached" };
        if (theme == THEME_ZOMBIE)
            return new String[] { "Zombie_Aberrant_Small" };
        return null;
    }

    /** Counts for getFixedCompBases; same length. */
    private int[] getFixedCompCounts(CometTier tier, int theme) {
        if (theme == THEME_VOID)
            return (tier == CometTier.EPIC) ? new int[] { 2, 3 } : new int[] { 2, 2 };
        if (theme == THEME_ICE)
            return new int[] { 1, 2, 1, 2 };
        if (theme == THEME_BURNT_LEGENDARY)
            return new int[] { 3, 1, 4, 2 };
        // Outlander Legendary: default 0 Priest; 30% of waves get 1 Priest (overridden
        // in spawnWave)
        if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY)
            return new int[] { 4, 1, 2, 0 };
        if (theme == THEME_LAVA)
            return new int[] { 1, 2, 1 };
        if (theme == THEME_EARTH)
            return new int[] { 1, 2, 4 };
        if (theme == THEME_UNDEAD_RARE)
            return new int[] { 2, 1, 2 }; // Pig_Undead, Cow_Undead, Chicken_Undead (was 4,2,3; reduced – too many mobs)
        if (theme == THEME_UNDEAD_LEGENDARY)
            return new int[] { 8, 4, 6, 3 };
        if (theme == THEME_ZOMBIE)
            return new int[] { 5 };
        return null;
    }

    /**
     * Get wave 1 mob count. Sabertooth on Tier 1 spawns 3; Skeleton on Tier 1
     * spawns 4 (3 melee + 1 ranged); Void spawns 4 (2 Crawler + 2 Spectre); all
     * others use WAVE_MOB_COUNT.
     */
    private int getWave1MobCount(CometTier tier, int theme) {
        if (tier == CometTier.UNCOMMON && theme == THEME_SABERTOOTH) {
            return 4;
        }
        if (tier == CometTier.UNCOMMON && theme == THEME_SKELETON) {
            return 4;
        }
        if (theme == THEME_VOID) {
            return (tier == CometTier.EPIC) ? 5 : 4; // Epic: 2 Eye + 3 Spawn; else 2 Crawler + 2 Spectre
        }
        if (theme == THEME_ICE)
            return 6;
        if (theme == THEME_BURNT_LEGENDARY)
            return 10;
        if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY)
            return 7;
        if (theme == THEME_LAVA)
            return 4;
        if (theme == THEME_EARTH)
            return 7;
        if (theme == THEME_UNDEAD_RARE)
            return 5; // 2 Pig + 1 Cow + 2 Chicken (was 9; reduced)
        if (theme == THEME_UNDEAD_LEGENDARY)
            return 21;
        if (theme == THEME_ZOMBIE)
            return 5;
        return WAVE_MOB_COUNT;
    }

    /**
     * Get tier index (0-3) for array access.
     * IMPORTANT: RARE returns 1 (Tier 2), EPIC returns 2 (Tier 3)
     */
    private int getTierIndex(CometTier tier) {
        switch (tier) {
            case UNCOMMON:
                return 0; // Tier 1
            case RARE:
                return 1; // Tier 2 (easier)
            case EPIC:
                return 2; // Tier 3 (harder)
            case LEGENDARY:
                return 3; // Tier 4
            default:
                return 0;
        }
    }

    /**
     * Get tier-specific timeout in milliseconds
     */
    private long getTierTimeout(CometTier tier) {
        switch (tier) {
            case UNCOMMON:
                return TIER1_TIMEOUT;
            case RARE:
                return TIER2_TIMEOUT;
            case EPIC:
                return TIER3_TIMEOUT;
            case LEGENDARY:
                return TIER4_TIMEOUT;
            default:
                return TIER1_TIMEOUT;
        }
    }

    /**
     * Get boss NPC type for tier and theme.
     * Boss selection is based on the theme, but tier suffix is from the comet tier.
     * Theme-specific bosses: Trork always gets Trork_Chieftain, Skeleton Burnt
     * always gets Praetorian.
     */
    private String getBossForTierAndTheme(CometTier tier, int theme) {
        String baseBoss;

        // Theme-specific boss overrides
        if (theme == THEME_TRORK) {
            // Trork theme always uses Trork_Chieftain
            baseBoss = THEME_TRORK_BOSS;
        } else if (theme == THEME_SPIDER) {
            // Spider theme always uses Spider_Broodmother
            baseBoss = THEME_SPIDER_BOSS;
        } else if (theme == THEME_SKELETON_BURNT) {
            // Skeleton Burnt theme always uses Skeleton_Burnt_Praetorian
            baseBoss = THEME_SKELETON_BURNT_BOSS;
        } else if (theme == THEME_VOID) {
            // Epic Void: Shadow_Knight; Uncommon/Rare: Spawn_Void
            if (tier == CometTier.EPIC)
                return applyTierSuffix("Shadow_Knight", tier);
            return applyTierSuffix(THEME_VOID_BOSS, tier);
        } else if (theme == THEME_OUTLANDER) {
            baseBoss = RANDOM.nextBoolean() ? "Werewolf" : "Yeti";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_ICE) {
            baseBoss = "Spirit_Frost";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_BURNT_LEGENDARY) {
            baseBoss = "Skeleton_Burnt_Praetorian";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_LAVA) {
            baseBoss = "Toad_Rhino_Magma";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_EARTH) {
            baseBoss = "Hedera";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_UNDEAD_RARE) {
            baseBoss = "Golem_Crystal_Thunder";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_UNDEAD_LEGENDARY) {
            baseBoss = "Wraith";
            return applyTierSuffix(baseBoss, tier);
        } else if (theme == THEME_ZOMBIE) {
            baseBoss = "Zombie_Aberrant";
            return applyTierSuffix(baseBoss, tier);
        } else {
            // Other themes use tier-appropriate boss pool
            int themeNativeTier = THEME_NATIVE_TIER[theme];
            if (themeNativeTier == 1) {
                // Tier 1 native themes use Tier 1 boss pool
                baseBoss = TIER1_BOSSES_BASE[RANDOM.nextInt(TIER1_BOSSES_BASE.length)];
            } else if (themeNativeTier == 2) {
                // Tier 2 native themes (non-Trork) use Tier 2 boss pool
                baseBoss = TIER2_BOSSES_BASE[RANDOM.nextInt(TIER2_BOSSES_BASE.length)];
            } else if (themeNativeTier == 3) {
                // Tier 3 native themes use Werewolf
                baseBoss = TIER3_BOSS_BASE;
            } else {
                // Tier 4 native themes (non-Skeleton Burnt) use Tier 4 boss pool
                baseBoss = TIER4_BOSSES_BASE[RANDOM.nextInt(TIER4_BOSSES_BASE.length)];
            }
        }

        // Apply the comet's tier suffix (boss scales to comet tier, not theme's native
        // tier)
        return applyTierSuffix(baseBoss, tier);
    }

    /**
     * Get all boss NPC types for this tier and theme. Most themes return 1;
     * Legendary Outlander returns 2 (Werewolf + Yeti).
     */
    private java.util.List<String> getBosses(CometTier tier, int theme) {
        if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY) {
            return java.util.Arrays.asList(applyTierSuffix("Werewolf", tier), applyTierSuffix("Yeti", tier));
        }
        return java.util.Collections.singletonList(getBossForTierAndTheme(tier, theme));
    }

    public void updateWaveCountdown(Store<EntityStore> store, Ref<EntityStore> playerRef, WaveData waveData) {
        // Attempt to re-find the player if their reference is invalid (e.g. died and
        // respawned)
        Ref<EntityStore> currentPlayerRef = playerRef;
        if (currentPlayerRef == null || !currentPlayerRef.isValid()) {
            java.util.UUID ownerUUID = cometOwners.get(waveData.blockPos);
            if (ownerUUID != null) {
                try {
                    com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                            .getExternalData()).getWorld();
                    for (com.hypixel.hytale.server.core.entity.entities.Player p : world.getPlayers()) {
                        if (ownerUUID.equals(p.getUuid())) {
                            currentPlayerRef = p.getReference();
                            LOGGER.info("[CometWaveManager] Re-synchronized player " + p.getDisplayName()
                                    + " for wave at " + waveData.blockPos + " after respawn");
                            // We can't update waveData.playerRef because it's final (usually),
                            // but we can use currentPlayerRef for this run.
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error re-synchronizing player: " + e.getMessage());
                }
            }
        }

        // PlayerRef can be null when player is dead (component may be removed/invalid);
        // we still run
        // mob counting and completion so loot/finish trigger. UI updates are skipped
        // when null.
        PlayerRef playerRefComponent = (currentPlayerRef != null && currentPlayerRef.isValid())
                ? store.getComponent(currentPlayerRef, PlayerRef.getComponentType())
                : null;

        // Count remaining mobs - check for DeathComponent (more reliable than
        // EntityRemoveEvent)
        // Remove dead mobs (those with DeathComponent) and invalid refs
        int beforeCleanup = waveData.spawnedMobs.size();
        waveData.spawnedMobs.removeIf(ref -> {
            if (ref == null || !ref.isValid()) {
                return true; // Remove invalid refs
            }
            // Check if entity has DeathComponent (is dead)
            com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent deathComponent = store.getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType());
            if (deathComponent != null) {
                LOGGER.info("Found dead mob in wave at " + waveData.blockPos);
                return true; // Remove dead mobs
            }
            return false; // Keep alive mobs
        });
        int afterCleanup = waveData.spawnedMobs.size();
        if (beforeCleanup != afterCleanup) {
            LOGGER.info("Cleaned up " + (beforeCleanup - afterCleanup) + " dead/invalid mob refs");
        }

        // Count remaining alive mobs
        int remaining = 0;
        for (Ref<EntityStore> mobRef : waveData.spawnedMobs) {
            if (mobRef != null && mobRef.isValid()) {
                // Double-check it's not dead
                com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent deathComponent = store.getComponent(
                        mobRef, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType());
                if (deathComponent == null) {
                    // Not dead, check if it's still an NPC
                    com.hypixel.hytale.server.npc.entities.NPCEntity npc = store.getComponent(mobRef,
                            com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                    if (npc != null) {
                        remaining++;
                    }
                }
            }
        }

        LOGGER.info("Wave at " + waveData.blockPos + ": " + remaining + " mobs remaining (out of "
                + waveData.spawnedMobs.size() + " in list)");

        // Check if mob count changed (real-time detection)
        boolean mobCountChanged = (remaining != waveData.previousRemainingCount);
        waveData.remainingCount = remaining;
        waveData.previousRemainingCount = remaining;

        // Get tier-specific timeout from config
        CometTier tier = cometTiers.getOrDefault(waveData.blockPos, CometTier.UNCOMMON);
        long tierTimeout = WaveThemeProvider.getTimeoutMillis(tier);

        // Check if wave has exceeded tier-specific timeout
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - waveData.startTime;
        long remainingTime = tierTimeout - elapsedTime;

        if (remainingTime <= 0) {
            LOGGER.warning("Wave at " + waveData.blockPos + " exceeded " + (tierTimeout / 1000)
                    + " second timeout! Destroying comet.");
            // Timeout reached - destroy comet and clean up
            // Must execute on WorldThread
            try {
                com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                        .getExternalData()).getWorld();
                final Store<EntityStore> finalStore = store;
                final WaveData finalWaveData = waveData;
                world.execute(() -> {
                    destroyCometOnTimeout(finalStore, finalWaveData);
                });
            } catch (Exception e) {
                LOGGER.warning("Error executing timeout on WorldThread: " + e.getMessage());
            }
            return;
        }

        // Calculate killed count using actual initial spawn count
        int totalMobs = waveData.initialSpawnCount;
        int killedMobs = totalMobs - remaining;

        // Calculate remaining time in seconds
        int remainingSeconds = (int) (remainingTime / 1000);
        String timeText = remainingSeconds + "s";

        // Always update when mob count changes (real-time), or update timer every 5
        // seconds
        long timeSinceLastUpdate = currentTime - waveData.lastTimerUpdate;
        boolean shouldUpdateTimer = mobCountChanged || (timeSinceLastUpdate >= 5000);

        if (shouldUpdateTimer) {
            // Update last timer update time (always, so 5s periodic logic works)
            waveData.lastTimerUpdate = currentTime;

            // Only update title if player is available (dead players have null PlayerRef)
            if (playerRefComponent != null) {
                Message primaryTitle;
                Message secondaryTitle;

                // Determine wave type for display
                String themeId = cometThemes.get(waveData.blockPos);
                boolean isBossWave = WaveThemeProvider.isWaveBoss(themeId, waveData.currentWaveIndex);

                if (isBossWave) {
                    // Boss wave display
                    String waveLabel = waveData.totalWaveCount > 2
                            ? "Boss Wave " + waveData.currentWave + "/" + waveData.totalWaveCount
                            : "Boss Wave!";
                    primaryTitle = Message.raw(waveLabel);
                    secondaryTitle = Message.raw("Boss: " + (remaining > 0 ? "Alive" : "Defeated") + " | Time: " + timeText);
                } else {
                    // Normal wave display
                    String waveLabel = waveData.totalWaveCount > 2
                            ? "Wave " + waveData.currentWave + "/" + waveData.totalWaveCount + " - " + waveData.themeName
                            : waveData.themeName + " Incoming!";
                    primaryTitle = Message.raw(waveLabel);
                    secondaryTitle = Message.raw("Mobs: " + killedMobs + "/" + totalMobs + " | Time: " + timeText);
                }

                LOGGER.info("Updating title: Wave=" + waveData.currentWave + "/" + waveData.totalWaveCount +
                        " (boss=" + isBossWave + ") | Mobs=" + killedMobs + "/" + totalMobs +
                        " | Time: " + timeText + (mobCountChanged ? " (mob died - real-time)" : " (periodic)"));

                EventTitleUtil.hideEventTitleFromPlayer(playerRefComponent, 0.0F);
                EventTitleUtil.showEventTitleToPlayer(
                        playerRefComponent,
                        primaryTitle,
                        secondaryTitle,
                        true,
                        null,
                        999.0F,
                        0.0F,
                        0.0F);
            }
        }

        // If all mobs are dead, check if we need to spawn next wave or complete
        if (remaining == 0) {
            if (waveData.hasMoreWaves()) {
                // More waves to spawn - advance to next wave
                LOGGER.info("=== Wave " + waveData.currentWave + " complete! Spawning wave " +
                        (waveData.currentWave + 1) + "/" + waveData.totalWaveCount + " at " + waveData.blockPos + " ===");
                spawnNextWave(store, playerRef, waveData);
                // Don't continue processing - spawnNextWave will handle the next update
                return;
            } else {
                // All waves complete - finish the comet
                LOGGER.info("=== All " + waveData.totalWaveCount + " waves defeated! Completing comet at " +
                        waveData.blockPos + " ===");
                completeWave(store, playerRefComponent, waveData);
            }
        }
        // Note: Timer updates when mobs die (frequent) or when 5 seconds have passed
        // (checked above)
        // No periodic scheduling to avoid lag - updates happen naturally when mobs die
    }

    /**
     * Destroy comet when wave times out (1 minute elapsed)
     */
    private void destroyCometOnTimeout(Store<EntityStore> store, WaveData waveData) {
        Vector3i blockPos = waveData.blockPos;
        LOGGER.info("Destroying comet at " + blockPos + " due to timeout");

        // Despawn all spawned mobs (wave 1 and/or boss) when the wave fails
        int despawned = 0;
        try {
            java.lang.reflect.Method takeMethod = store.getClass().getDeclaredMethod("takeCommandBuffer");
            takeMethod.setAccessible(true);
            com.hypixel.hytale.component.CommandBuffer<EntityStore> cb = (com.hypixel.hytale.component.CommandBuffer<EntityStore>) takeMethod
                    .invoke(store);
            if (cb != null) {
                for (Ref<EntityStore> mobRef : waveData.spawnedMobs) {
                    if (mobRef != null && mobRef.isValid()) {
                        try {
                            cb.removeEntity(mobRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                            despawned++;
                        } catch (Exception e) {
                            LOGGER.warning("Failed to remove mob on wave fail: " + e.getMessage());
                        }
                    }
                }
                java.lang.reflect.Method consumeMethod = cb.getClass().getDeclaredMethod("consume");
                consumeMethod.setAccessible(true);
                consumeMethod.invoke(cb);
            }
        } catch (Exception e) {
            LOGGER.warning("Could not get CommandBuffer to despawn mobs on wave fail: " + e.getMessage());
        }
        if (despawned > 0) {
            LOGGER.info("Despawned " + despawned + " mobs due to wave failure at " + blockPos);
        }

        // Remove from active tracking
        activeComets.remove(blockPos);
        activeWaves.remove(blockPos);

        // Break the comet block
        try {
            com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                    .getExternalData()).getWorld();

            // Remove map marker (must be done before removing cometOwners)
            removeCometMapMarker(world, blockPos);
            world.breakBlock(blockPos.x, blockPos.y, blockPos.z, 0);
            LOGGER.info("Broke comet block at " + blockPos + " due to timeout");

            // Clean up all tracking data
            cometOwners.remove(blockPos);
            cometTiers.remove(blockPos);
            cometThemes.remove(blockPos);

            // Unregister from despawn tracker
            CometDespawnTracker.getInstance().unregisterComet(blockPos);
        } catch (Exception e) {
            LOGGER.severe("Error breaking comet block on timeout: " + e.getMessage());
            e.printStackTrace();
        }

        // Show "Wave Failed!" message and hide the "Wave Active!" title
        if (waveData.playerRef != null && waveData.playerRef.isValid()) {
            try {
                PlayerRef playerRefComponent = store.getComponent(waveData.playerRef, PlayerRef.getComponentType());
                if (playerRefComponent != null) {
                    // Hide the current "Wave Active!" title first
                    EventTitleUtil.hideEventTitleFromPlayer(playerRefComponent, 0.0F);

                    // Show "Wave Failed!" message
                    Message primaryTitle = Message.raw("Wave Failed!");
                    Message secondaryTitle = Message.raw("Time's Up!");

                    EventTitleUtil.showEventTitleToPlayer(
                            playerRefComponent,
                            primaryTitle,
                            secondaryTitle,
                            true, // isMajor = true to make it prominent
                            null,
                            3.0F, // Show for 3 seconds
                            0.0F, // No fade in
                            0.5F // Fade out
                    );

                    LOGGER.info("Showed 'Wave Failed!' message on timeout");

                    // Schedule hide after 3 seconds
                    com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                            .getExternalData()).getWorld();

                    final PlayerRef finalPlayerRef = playerRefComponent;
                    com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        try {
                            world.execute(() -> {
                                EventTitleUtil.hideEventTitleFromPlayer(finalPlayerRef, 0.0F);
                                LOGGER.info("Hid 'Wave Failed!' message after 3 seconds");
                            });
                        } catch (Exception e) {
                            LOGGER.warning("Error hiding failed message: " + e.getMessage());
                        }
                    }, 3L, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                LOGGER.warning("Error showing failed message on timeout: " + e.getMessage());
            }
        }
    }

    /**
     * Spawn the next wave in a multi-wave encounter.
     * Determines wave type and calls appropriate spawn method.
     */
    private void spawnNextWave(Store<EntityStore> store, Ref<EntityStore> playerRef, WaveData waveData) {
        // Advance to next wave
        waveData.advanceToNextWave();

        Vector3i blockPos = waveData.blockPos;
        String themeId = cometThemes.get(blockPos);
        if (themeId == null) themeId = "skeleton";

        CometTier tier = cometTiers.getOrDefault(blockPos, CometTier.UNCOMMON);
        int waveIndex = waveData.currentWaveIndex;

        LOGGER.info("=== SPAWNING WAVE " + waveData.currentWave + "/" + waveData.totalWaveCount +
                " (index " + waveIndex + ") ===");

        // Check wave type and spawn accordingly
        if (WaveThemeProvider.isWaveBoss(themeId, waveIndex)) {
            // Boss wave
            LOGGER.info("Wave " + waveData.currentWave + " is a BOSS wave");
            spawnBossWaveAtIndex(store, playerRef, waveData, waveIndex);
        } else {
            // Normal wave (mob wave)
            LOGGER.info("Wave " + waveData.currentWave + " is a NORMAL wave");
            spawnNormalWaveAtIndex(store, playerRef, waveData, waveIndex);
        }
    }

    /**
     * Spawn a normal (mob) wave at a specific wave index.
     */
    private void spawnNormalWaveAtIndex(Store<EntityStore> store, Ref<EntityStore> playerRef,
            WaveData waveData, int waveIndex) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.severe("NPCPlugin not available for normal wave!");
            return;
        }

        Vector3i blockPos = waveData.blockPos;
        CometTier tier = cometTiers.getOrDefault(blockPos, CometTier.UNCOMMON);
        String themeId = cometThemes.get(blockPos);
        if (themeId == null) themeId = "skeleton";

        // Get mob list for this wave
        String[] mobList = WaveThemeProvider.getMobListForWave(tier, themeId, waveIndex);
        if (mobList == null || mobList.length == 0) {
            LOGGER.warning("No mobs found for wave " + waveData.currentWave + " in theme " + themeId);
            return;
        }

        LOGGER.info("Spawning " + mobList.length + " mobs for wave " + waveData.currentWave);

        // Get spawn radius
        double[] radiusRange = WaveThemeProvider.getSpawnRadius(tier);
        double minRadius = radiusRange[0];
        double maxRadius = radiusRange[1];

        Vector3d centerPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 1, blockPos.z + 0.5);

        // Shuffle for randomization
        java.util.List<String> mobListShuffled = new java.util.ArrayList<>(java.util.Arrays.asList(mobList));
        java.util.Collections.shuffle(mobListShuffled, RANDOM);

        com.hypixel.hytale.server.core.universe.world.World world = null;
        try {
            world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData())
                    .getWorld();
        } catch (Exception e) {
            LOGGER.warning("Could not get World for mob spawn validation: " + e.getMessage());
        }

        int rangedCount = 0;
        String[] rangedMobs = { "Archer", "Archmage", "Lobber", "Shaman", "Mage", "Ranger",
                "Hunter", "Stalker", "Priest", "Gunner", "Alchemist" };

        List<Vector3d> successPositions = new ArrayList<>();
        List<FailedSpawnInfo> failedSpawns = new ArrayList<>();

        for (int i = 0; i < mobListShuffled.size(); i++) {
            String npcType = mobListShuffled.get(i);

            // Enforce ranged limit
            if (rangedCount >= MAX_RANGED_PER_WAVE) {
                boolean isRanged = false;
                for (String ranged : rangedMobs) {
                    if (npcType.contains(ranged)) {
                        isRanged = true;
                        break;
                    }
                }
                if (isRanged) {
                    // Find a non-ranged replacement
                    for (String mob : mobListShuffled) {
                        boolean mobIsRanged = false;
                        for (String ranged : rangedMobs) {
                            if (mob.contains(ranged)) {
                                mobIsRanged = true;
                                break;
                            }
                        }
                        if (!mobIsRanged) {
                            npcType = mob;
                            break;
                        }
                    }
                }
            }

            // Count ranged
            for (String ranged : rangedMobs) {
                if (npcType.contains(ranged)) {
                    rangedCount++;
                    break;
                }
            }

            double angle = (2.0 * Math.PI * i) / mobListShuffled.size();
            double radius = minRadius + (RANDOM.nextDouble() * (maxRadius - minRadius));
            Vector3d spawnPos = new Vector3d(centerPos.x + Math.cos(angle) * radius, centerPos.y,
                    centerPos.z + Math.sin(angle) * radius);

            Vector3d toSpawn = spawnPos;
            if (world != null) {
                Vector3d v = CometSpawnUtil.findValidMobSpawn(world, spawnPos, 11);
                if (v != null) {
                    toSpawn = v;
                } else {
                    failedSpawns.add(new FailedSpawnInfo(npcType,
                            new Vector3f(0.0f, (float) (angle + Math.PI), 0.0f), isRangedMob(npcType)));
                    continue;
                }
            }

            Vector3f rotation = new Vector3f(0.0f, (float) (angle + Math.PI), 0.0f);
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result =
                    spawnCometNPC(store, npcPlugin, npcType, npcType, toSpawn, rotation, themeId, tier, false);

            if (result != null && result.first() != null) {
                waveData.spawnedMobs.add(result.first());
                successPositions.add(toSpawn);
            }
        }

        // Retry failed spawns
        if (!failedSpawns.isEmpty() && !successPositions.isEmpty() && world != null) {
            for (FailedSpawnInfo f : failedSpawns) {
                Vector3d base = successPositions.get(RANDOM.nextInt(successPositions.size()));
                double dx = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                double dz = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                Vector3d retryPos = CometSpawnUtil.findValidMobSpawn(world,
                        new Vector3d(base.x + dx, base.y, base.z + dz), 11);
                if (retryPos != null) {
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> res =
                            spawnCometNPC(store, npcPlugin, f.npcType, f.npcType, retryPos, f.rotation, themeId, tier, false);
                    if (res != null && res.first() != null) {
                        waveData.spawnedMobs.add(res.first());
                        successPositions.add(retryPos);
                    }
                }
            }
        }

        waveData.initialSpawnCount = waveData.spawnedMobs.size();
        waveData.previousRemainingCount = waveData.initialSpawnCount;
        LOGGER.info("Spawned " + waveData.initialSpawnCount + " mobs for wave " + waveData.currentWave);

        // Start tracking and force immediate title update
        waveData.lastTimerUpdate = 0;
        updateWaveCountdown(store, playerRef, waveData);
    }

    /**
     * Spawn a boss wave at a specific wave index.
     */
    private void spawnBossWaveAtIndex(Store<EntityStore> store, Ref<EntityStore> playerRef,
            WaveData waveData, int waveIndex) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.severe("NPCPlugin not available for boss wave!");
            return;
        }

        Vector3i blockPos = waveData.blockPos;
        CometTier tier = cometTiers.getOrDefault(blockPos, CometTier.UNCOMMON);
        String themeId = cometThemes.get(blockPos);
        if (themeId == null) themeId = "skeleton";

        // Get bosses for this specific wave
        java.util.List<String> bosses = WaveThemeProvider.getBossesForWave(tier, themeId, waveIndex);
        if (bosses == null || bosses.isEmpty()) {
            // Fallback to legacy
            int legacyTheme = getLegacyThemeIndex(themeId);
            if (legacyTheme >= 0) {
                bosses = getBossesLegacy(tier, legacyTheme);
            } else {
                bosses = java.util.Collections.singletonList(applyTierSuffix("Bear_Polar", tier));
            }
        }

        LOGGER.info("Spawning " + bosses.size() + " boss(es) for wave " + waveData.currentWave);
        waveData.previousRemainingCount = bosses.size();
        waveData.initialSpawnCount = 0;

        com.hypixel.hytale.server.core.universe.world.World world = null;
        try {
            world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData())
                    .getWorld();
        } catch (Exception e) {
            LOGGER.warning("Could not get World for boss spawn validation: " + e.getMessage());
        }

        Vector3d centerPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 1, blockPos.z + 0.5);
        Vector3f rotation = new Vector3f(0.0f, 0.0f, 0.0f);
        List<Vector3d> successPositions = new ArrayList<>();
        List<String> failedBosses = new ArrayList<>();

        for (int b = 0; b < bosses.size(); b++) {
            String bossType = bosses.get(b);
            double ox = (bosses.size() > 1 && b == 1) ? 1.5 : 0;
            Vector3d pos = new Vector3d(centerPos.x + ox, centerPos.y, centerPos.z);
            Vector3d toSpawn = pos;

            if (world != null) {
                Vector3d v = CometSpawnUtil.findValidMobSpawn(world, pos, 11);
                if (v != null) {
                    toSpawn = v;
                } else {
                    failedBosses.add(bossType);
                    continue;
                }
            }

            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result =
                    spawnCometNPC(store, npcPlugin, bossType, bossType, toSpawn, rotation, themeId, tier, true);

            if (result != null && result.first() != null) {
                waveData.spawnedMobs.add(result.first());
                successPositions.add(toSpawn);
            }
        }

        // Retry failed boss spawns
        if (!failedBosses.isEmpty() && !successPositions.isEmpty() && world != null) {
            for (String bossType : failedBosses) {
                Vector3d base = successPositions.get(RANDOM.nextInt(successPositions.size()));
                double dx = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                double dz = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                Vector3d retryPos = CometSpawnUtil.findValidMobSpawn(world,
                        new Vector3d(base.x + dx, base.y, base.z + dz), 11);
                if (retryPos != null) {
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> res =
                            spawnCometNPC(store, npcPlugin, bossType, bossType, retryPos, rotation, themeId, tier, true);
                    if (res != null && res.first() != null) {
                        waveData.spawnedMobs.add(res.first());
                        successPositions.add(retryPos);
                    }
                }
            }
        }

        waveData.initialSpawnCount = waveData.spawnedMobs.size();
        waveData.previousRemainingCount = waveData.initialSpawnCount;
        LOGGER.info("Spawned " + waveData.initialSpawnCount + " boss(es) for wave " + waveData.currentWave);

        // Start tracking and force immediate title update
        waveData.lastTimerUpdate = 0;
        updateWaveCountdown(store, playerRef, waveData);
    }

    /**
     * Spawn boss wave (Wave 2) after normal wave completes
     * @deprecated Use spawnNextWave or spawnBossWaveAtIndex instead
     */
    private void spawnBossWave(Store<EntityStore> store, Ref<EntityStore> playerRef, WaveData waveData) {
        LOGGER.info("=== SPAWNING BOSS WAVE ===");
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.severe("NPCPlugin not available for boss wave!");
            return;
        }

        Vector3i blockPos = waveData.blockPos;
        CometTier tier = cometTiers.getOrDefault(blockPos, CometTier.UNCOMMON);
        String themeId = cometThemes.get(blockPos);
        if (themeId == null)
            themeId = "skeleton"; // Default fallback
        LOGGER.info("Boss wave for tier: " + tier.getName() + " theme: " + themeId + " at " + blockPos);

        // Clear Wave 1 mobs from list
        waveData.spawnedMobs.clear();
        LOGGER.info("Cleared Wave 1 mobs, list size: " + waveData.spawnedMobs.size());

        // Update to Wave 2
        waveData.currentWave = 2;
        waveData.startTime = System.currentTimeMillis(); // Reset timer for boss wave
        waveData.lastTimerUpdate = waveData.startTime;

        // Get bosses from config, with legacy fallback
        java.util.List<String> bosses = WaveThemeProvider.getBossesForTheme(tier, themeId);
        if (bosses == null || bosses.isEmpty()) {
            int legacyTheme = getLegacyThemeIndex(themeId);
            if (legacyTheme >= 0) {
                bosses = getBossesLegacy(tier, legacyTheme);
            } else {
                bosses = java.util.Collections.singletonList(applyTierSuffix("Bear_Polar", tier));
            }
        }
        waveData.previousRemainingCount = bosses.size();
        waveData.initialSpawnCount = 0;

        com.hypixel.hytale.server.core.universe.world.World world = null;
        try {
            world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData())
                    .getWorld();
        } catch (Exception e) {
            LOGGER.warning("Could not get World for boss spawn validation: " + e.getMessage());
        }

        Vector3d centerPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 1, blockPos.z + 0.5);
        Vector3f rotation = new Vector3f(0.0f, 0.0f, 0.0f);
        int spawned = 0;
        List<Vector3d> successPositions = new ArrayList<>();
        List<String> failedBosses = new ArrayList<>();
        for (int b = 0; b < bosses.size(); b++) {
            String bossType = bosses.get(b);
            double ox = (bosses.size() > 1 && b == 1) ? 1.5 : 0;
            Vector3d pos = new Vector3d(centerPos.x + ox, centerPos.y, centerPos.z);
            Vector3d toSpawn = pos;
            if (world != null) {
                Vector3d v = CometSpawnUtil.findValidMobSpawn(world, pos, 11);
                if (v != null)
                    toSpawn = v;
                else {
                    failedBosses.add(bossType);
                    LOGGER.info("No valid boss spawn at " + pos + ", will retry near success: " + bossType);
                    continue;
                }
            }
            // Boss IDs are base IDs without tier suffixes
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result = spawnCometNPC(
                    store, npcPlugin, bossType, bossType, toSpawn, rotation, themeId, tier, true);
            if (result != null && result.first() != null) {
                waveData.spawnedMobs.add(result.first());
                successPositions.add(toSpawn);
                spawned++;
                LOGGER.info("Spawned boss " + bossType + " at " + toSpawn);
            } else {
                LOGGER.warning("Failed to spawn boss: " + bossType);
            }
        }
        // Retry failed bosses near a successful one
        if (!failedBosses.isEmpty() && !successPositions.isEmpty() && world != null) {
            for (String bossType : failedBosses) {
                Vector3d base = successPositions.get(RANDOM.nextInt(successPositions.size()));
                double dx = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                double dz = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble());
                Vector3d newPref = new Vector3d(base.x + dx, base.y, base.z + dz);
                Vector3d retryPos = CometSpawnUtil.findValidMobSpawn(world, newPref, 11);
                if (retryPos != null) {
                    // Boss IDs are base IDs without tier suffixes
                    Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> res = spawnCometNPC(
                            store, npcPlugin, bossType, bossType, retryPos, rotation, themeId, tier, true);
                    if (res != null && res.first() != null) {
                        waveData.spawnedMobs.add(res.first());
                        successPositions.add(retryPos);
                        spawned++;
                        LOGGER.info("Spawned boss " + bossType + " at " + retryPos + " (retry near success)");
                    }
                }
            }
        }
        waveData.initialSpawnCount = spawned;

        if (spawned > 0) {
            // Always schedule countdown so boss death can trigger completion even if player
            // is dead
            try {
                com.hypixel.hytale.server.core.universe.world.World worldForRun = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                        .getExternalData()).getWorld();
                final Store<EntityStore> finalStore = store;
                final Ref<EntityStore> finalPlayerRef = playerRef;
                final WaveData finalWaveData = waveData;
                com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    try {
                        worldForRun.execute(() -> {
                            finalWaveData.lastTimerUpdate = 0;
                            updateWaveCountdown(finalStore, finalPlayerRef, finalWaveData);
                        });
                    } catch (Exception e) {
                        LOGGER.warning("Error updating boss wave UI: " + e.getMessage());
                    }
                }, 100L, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOGGER.warning("Error scheduling boss wave UI update: " + e.getMessage());
            }
        } else {
            // No bosses spawned – complete immediately (playerRef can be null if player
            // dead)
            PlayerRef pr = (playerRef != null && playerRef.isValid())
                    ? store.getComponent(playerRef, PlayerRef.getComponentType())
                    : null;
            completeWave(store, pr, waveData);
        }
    }

    /**
     * Complete the wave: drop loot, break block, and optionally show title to the
     * player.
     *
     * @param playerRef can be null if the player is dead; loot and block break
     *                  still run,
     *                  only the completion title is skipped.
     */
    private void completeWave(Store<EntityStore> store, PlayerRef playerRef, WaveData waveData) {
        Vector3i blockPos = waveData.blockPos;
        CometTier tier = cometTiers.getOrDefault(blockPos, CometTier.UNCOMMON);
        LOGGER.info("[CometWaveManager] completeWave: Tier=" + tier.getName() + " for comet at " + blockPos);

        activeWaves.remove(blockPos);

        // Always drop items and break the block (even if player is dead)
        java.util.List<String> droppedItems = dropRewardsAndBreakBlock(store, blockPos, waveData, tier);
        LOGGER.info("[CometWaveManager] Rewards dropped: " + (droppedItems != null ? droppedItems.size() : "null")
                + " items.");

        // Show completion title only when player is available (e.g. not dead)
        if (playerRef != null) {
            // 1. Show "Wave Complete!" as main title
            Message primaryTitle = Message.raw("Wave Complete!");
            Message secondaryTitle = Message.raw("Loot Dropped!");

            EventTitleUtil.hideEventTitleFromPlayer(playerRef, 0.0F);
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef,
                    primaryTitle,
                    secondaryTitle,
                    true, // isMajor
                    null,
                    8.0F,
                    0.2F,
                    0.5F);

            // 2. Show Loot in Chat
            Message header = Message.empty()
                .insert(Message.raw("[Comet] ").color("#FFAA00"))
                .insert(Message.raw("Wave Complete! Your rewards:").color("#FFFFFF"));
            playerRef.sendMessage(header);
            for (String item : droppedItems) {
                Message itemMsg = Message.empty()
                    .insert(Message.raw(" - ").color("#AAAAAA"))
                    .insert(Message.raw(item).color("#FFFFFF"));
                playerRef.sendMessage(itemMsg);
            }

            // 3. Schedule removal of title after 8 seconds
            try {
                com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                        .getExternalData()).getWorld();
                final PlayerRef finalPlayerRef = playerRef;
                com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    try {
                        world.execute(() -> {
                            EventTitleUtil.hideEventTitleFromPlayer(finalPlayerRef, 0.0F);
                        });
                    } catch (Exception e) {
                        LOGGER.warning("Error hiding success messages: " + e.getMessage());
                    }
                }, 8L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warning("Error scheduling message hide: " + e.getMessage());
            }
        } else {
            LOGGER.info("Skipping completion title (player not available); loot dropped at " + blockPos);
        }
    }

    /**
     * Generate structured rewards for a tier using config settings.
     * Checks for theme-specific reward overrides first, then falls back to global tier rewards.
     */
    private void generateTierRewards(CometTier tier, String themeId,
            java.util.List<com.hypixel.hytale.server.core.inventory.ItemStack> allItems,
            java.util.List<String> droppedItemIds) {

        // Get tier number for config lookup
        int tierNum;
        switch (tier) {
            case UNCOMMON:
                tierNum = 1;
                break;
            case RARE:
                tierNum = 2;
                break;
            case EPIC:
                tierNum = 3;
                break;
            case LEGENDARY:
                tierNum = 4;
                break;
            default:
                tierNum = 1;
        }

        com.cometmod.config.TierRewards rewards = null;

        // Check for theme-specific reward override first
        if (themeId != null && WaveThemeProvider.hasRewardOverride(themeId, tier)) {
            rewards = WaveThemeProvider.getRewardOverride(themeId, tier);
            if (rewards != null) {
                LOGGER.info("Using theme reward override for '" + themeId + "' tier " + tierNum);
            }
        }

        // Fall back to global tier rewards from config
        if (rewards == null) {
            CometConfig config = CometConfig.getInstance();
            if (config != null) {
                rewards = config.getTierRewards(tierNum);
                LOGGER.info("Using config-based rewards for tier " + tierNum);
            }
        }

        // Fall back to default rewards if config not available
        if (rewards == null) {
            rewards = com.cometmod.config.TierRewards.getDefaultForTier(tierNum);
            LOGGER.info("Using default rewards for tier " + tierNum + " (config not available)");
        }

        // Generate rewards using the TierRewards class
        rewards.generateRewards(RANDOM, allItems, droppedItemIds);

        LOGGER.info("Generated " + allItems.size() + " reward items for tier " + tier.getName());
    }

    /**
     * Legacy generateTierRewardsHardcoded - kept for reference only
     * This code is no longer used - rewards are now loaded from config
     */
    private void generateTierRewardsLegacy(CometTier tier,
            java.util.List<com.hypixel.hytale.server.core.inventory.ItemStack> allItems,
            java.util.List<String> droppedItemIds) {
        if (tier == CometTier.UNCOMMON) {
            // Tier 1: Copper Ingots (5-7), Light Leather (2-3), Lesser Health Potion (1-2),
            // Bombs (3-4), Poison Bomb (1)
            int copperCount = 5 + RANDOM.nextInt(3); // 5-7
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Copper", copperCount));
            droppedItemIds.add("Copper Ingots x" + copperCount);

            int leatherCount = 2 + RANDOM.nextInt(2); // 2-3
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Leather_Light", leatherCount));
            droppedItemIds.add("Light Leather x" + leatherCount);

            int potionCount = 1 + RANDOM.nextInt(2); // 1-2
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Lesser", potionCount));
            droppedItemIds.add("Lesser Health Potion x" + potionCount);

            int bombCount = 3 + RANDOM.nextInt(2); // 3-4
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb", bombCount));
            droppedItemIds.add("Bombs x" + bombCount);

            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb_Potion_Poison", 1));
            droppedItemIds.add("Poison Potion Bomb x1");

        } else if (tier == CometTier.RARE) {
            // Tier 2: Iron Ingots (5-7), Medium Leather (2-3), Potion of Health (1-2),
            // Essence of Fire (3-4), Shadoweave Scraps (5), Bombs (4-5), Poison Bomb (1-2)
            // Bonus: Copper Ingots (2-4, 35% chance), Lesser Health Potion (1, 35% chance)

            int ironCount = 5 + RANDOM.nextInt(3); // 5-7
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Iron", ironCount));
            droppedItemIds.add("Iron Ingots x" + ironCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER2) {
                int copperCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(
                        new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Copper", copperCount));
                droppedItemIds.add("Copper Ingots x" + copperCount + " (bonus)");
            }

            int leatherCount = 2 + RANDOM.nextInt(2); // 2-3
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Leather_Medium", leatherCount));
            droppedItemIds.add("Medium Leather x" + leatherCount);

            int potionCount = 1 + RANDOM.nextInt(2); // 1-2
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health", potionCount));
            droppedItemIds.add("Potion of Health x" + potionCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER2) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Lesser", 1));
                droppedItemIds.add("Lesser Health Potion x1 (bonus)");
            }

            int essenceCount = 3 + RANDOM.nextInt(2); // 3-4
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Fire_Essence", essenceCount));
            droppedItemIds.add("Essence of Fire x" + essenceCount);

            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Fabric_Scrap_Shadoweave", 5));
            droppedItemIds.add("Shadoweave Scraps x5");

            int bombCount = 4 + RANDOM.nextInt(2); // 4-5
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb", bombCount));
            droppedItemIds.add("Bombs x" + bombCount);

            int poisonBombCount = 1 + RANDOM.nextInt(2); // 1-2
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb_Potion_Poison",
                    poisonBombCount));
            droppedItemIds.add("Poison Potion Bombs x" + poisonBombCount);

        } else if (tier == CometTier.EPIC) {
            // Tier 3: Cobalt/Thorium Ingots (5-7), Heavy Leather (2-3), Greater Health
            // Potion (1-2),
            // Essence of Fire (3-4), Shadoweave Scraps (5), Bombs (5-6), Poison Bomb (2-3)
            // Bonus: Copper (2-4, 30%), Iron (2-4, 30%), Lesser Health (1, 30%), Potion of
            // Health (1, 30%)

            // Randomly select Cobalt or Thorium
            String tier3Ore = (RANDOM.nextBoolean()) ? "Ingredient_Bar_Cobalt" : "Ingredient_Bar_Thorium";
            String oreName = tier3Ore.contains("Cobalt") ? "Cobalt" : "Thorium";
            int oreCount = 5 + RANDOM.nextInt(3); // 5-7
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack(tier3Ore, oreCount));
            droppedItemIds.add(oreName + " Ingots x" + oreCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER3) {
                int copperCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(
                        new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Copper", copperCount));
                droppedItemIds.add("Copper Ingots x" + copperCount + " (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER3) {
                int ironCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Iron", ironCount));
                droppedItemIds.add("Iron Ingots x" + ironCount + " (bonus)");
            }

            int leatherCount = 2 + RANDOM.nextInt(2); // 2-3
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Leather_Heavy", leatherCount));
            droppedItemIds.add("Heavy Leather x" + leatherCount);

            int potionCount = 1 + RANDOM.nextInt(2); // 1-2
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Greater", potionCount));
            droppedItemIds.add("Greater Health Potion x" + potionCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER3) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Lesser", 1));
                droppedItemIds.add("Lesser Health Potion x1 (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER3) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health", 1));
                droppedItemIds.add("Potion of Health x1 (bonus)");
            }

            int essenceCount = 3 + RANDOM.nextInt(2); // 3-4
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Fire_Essence", essenceCount));
            droppedItemIds.add("Essence of Fire x" + essenceCount);

            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Fabric_Scrap_Shadoweave", 5));
            droppedItemIds.add("Shadoweave Scraps x5");

            int bombCount = 5 + RANDOM.nextInt(2); // 5-6
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb", bombCount));
            droppedItemIds.add("Bombs x" + bombCount);

            int poisonBombCount = 2 + RANDOM.nextInt(2); // 2-3
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb_Potion_Poison",
                    poisonBombCount));
            droppedItemIds.add("Poison Potion Bombs x" + poisonBombCount);

        } else { // LEGENDARY
            // Tier 4: Adamantite Ingots (5-7), Heavy Leather (2-3), Large Potion of Health
            // (1-2),
            // Bombs (6-8), Poison Bomb (3-4)
            // Bonus: Copper (2-4, 25%), Iron (2-4, 25%), Cobalt/Thorium (2-4, 25%),
            // Lesser Health (1, 25%), Potion of Health (1, 25%), Greater Health (1, 25%)

            int adamantiteCount = 5 + RANDOM.nextInt(3); // 5-7
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Adamantite",
                    adamantiteCount));
            droppedItemIds.add("Adamantite Ingots x" + adamantiteCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                int copperCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(
                        new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Copper", copperCount));
                droppedItemIds.add("Copper Ingots x" + copperCount + " (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                int ironCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Bar_Iron", ironCount));
                droppedItemIds.add("Iron Ingots x" + ironCount + " (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                String tier3Ore = (RANDOM.nextBoolean()) ? "Ingredient_Bar_Cobalt" : "Ingredient_Bar_Thorium";
                String oreName = tier3Ore.contains("Cobalt") ? "Cobalt" : "Thorium";
                int oreCount = 2 + RANDOM.nextInt(3); // 2-4
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack(tier3Ore, oreCount));
                droppedItemIds.add(oreName + " Ingots x" + oreCount + " (bonus)");
            }

            int leatherCount = 2 + RANDOM.nextInt(2); // 2-3
            allItems.add(
                    new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Leather_Heavy", leatherCount));
            droppedItemIds.add("Heavy Leather x" + leatherCount);

            int potionCount = 1 + RANDOM.nextInt(2); // 1-2
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Large", potionCount));
            droppedItemIds.add("Large Potion of Health x" + potionCount);

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Lesser", 1));
                droppedItemIds.add("Lesser Health Potion x1 (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health", 1));
                droppedItemIds.add("Potion of Health x1 (bonus)");
            }

            if (RANDOM.nextDouble() < BONUS_DROP_CHANCE_TIER4) {
                allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Potion_Health_Greater", 1));
                droppedItemIds.add("Greater Health Potion x1 (bonus)");
            }

            int bombCount = 6 + RANDOM.nextInt(3); // 6-8
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb", bombCount));
            droppedItemIds.add("Bombs x" + bombCount);

            int poisonBombCount = 3 + RANDOM.nextInt(2); // 3-4
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Bomb_Potion_Poison",
                    poisonBombCount));
            droppedItemIds.add("Poison Potion Bombs x" + poisonBombCount);
        }
    }

    /**
     * Drop rewards from droplist and break the comet block
     * 
     * @return List of item IDs that were dropped
     */
    private java.util.List<String> dropRewardsAndBreakBlock(Store<EntityStore> store, Vector3i blockPos,
            WaveData waveData, CometTier tier) {
        java.util.List<String> droppedItemIds = new java.util.ArrayList<>();
        try {
            com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                    .getExternalData()).getWorld();

            java.util.List<com.hypixel.hytale.server.core.inventory.ItemStack> allItems = new java.util.ArrayList<>();

            // Get theme ID for potential reward override
            String themeId = cometThemes.get(blockPos);

            // Generate structured rewards based on tier (see REWARD_SYSTEM.md)
            // Checks for theme-specific reward override first
            generateTierRewards(tier, themeId, allItems, droppedItemIds);

            // Add guaranteed 5 Shards (all tiers)
            String shardId = tier.getShardId();
            allItems.add(new com.hypixel.hytale.server.core.inventory.ItemStack(shardId, 5));
            droppedItemIds.add(shardId + " x5");

            // Drop position (center of block)
            com.hypixel.hytale.math.vector.Vector3d dropPosition = new com.hypixel.hytale.math.vector.Vector3d(
                    blockPos.x + 0.5D, blockPos.y + 0.5D, blockPos.z + 0.5D);

            // Generate item drop entities
            LOGGER.info("[CometWaveManager] Generating item drops for " + allItems.size() + " item stacks...");
            com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>[] itemEntityHolders = com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
                    .generateItemDrops(
                            store,
                            allItems,
                            dropPosition,
                            com.hypixel.hytale.math.vector.Vector3f.ZERO);

            // Add item entities to the world immediately (no delay)
            if (itemEntityHolders != null && itemEntityHolders.length > 0) {
                for (com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder : itemEntityHolders) {
                    if (holder != null) {
                        store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
                    }
                }
                LOGGER.info("[CometWaveManager] Dropped " + itemEntityHolders.length + " item entities at " + blockPos);
            } else {
                LOGGER.warning(
                        "[CometWaveManager] No item entity holders generated for " + allItems.size() + " items!");
            }

            // Break the comet block (parameters: x, y, z, settings)
            world.breakBlock(blockPos.x, blockPos.y, blockPos.z, 0);
            LOGGER.info("Broke comet block at " + blockPos + " after dropping rewards");

            // Remove map marker and clean up all tracking so the marker disappears and
            // CometMarkerProvider stops including it
            removeCometMapMarker(world, blockPos);
            activeComets.remove(blockPos);
            cometTiers.remove(blockPos);
            cometOwners.remove(blockPos);
            cometThemes.remove(blockPos);

            // Unregister from despawn tracker
            CometDespawnTracker.getInstance().unregisterComet(blockPos);

            // Note: Title will auto-hide after its duration (5 seconds) set in
            // completeWave()
            // No need to manually schedule a hide - the EventTitleUtil handles it
            // automatically

        } catch (Exception e) {
            LOGGER.severe("Error dropping rewards and breaking block: " + e.getMessage());
            e.printStackTrace();
        }

        return droppedItemIds;
    }

    /**
     * Register tier for a comet block (called when block is spawned)
     * 
     * @param world     The world the comet is in
     * @param blockPos  The block position of the comet
     * @param tier      The tier of the comet
     * @param ownerUUID The UUID of the player who owns this comet (for marker
     *                  visibility)
     */
    public void registerCometTier(com.hypixel.hytale.server.core.universe.world.World world, Vector3i blockPos,
            CometTier tier, java.util.UUID ownerUUID) {
        cometTiers.put(blockPos, tier);
        if (ownerUUID != null) {
            cometOwners.put(blockPos, ownerUUID);
            LOGGER.info(
                    "Registered tier " + tier.getName() + " for comet at " + blockPos + " (owner: " + ownerUUID + ")");
        } else {
            LOGGER.info("Registered tier " + tier.getName() + " for comet at " + blockPos + " (no owner)");
        }

        // Add map marker for this comet to the specific player only
        addCometMapMarker(world, blockPos, tier, ownerUUID);
    }

    /**
     * Register tier for a comet block without owner (legacy/fallback - visible to
     * all)
     * 
     * @deprecated Use registerCometTier with ownerUUID instead
     */
    @Deprecated
    public void registerCometTier(com.hypixel.hytale.server.core.universe.world.World world, Vector3i blockPos,
            CometTier tier) {
        registerCometTier(world, blockPos, tier, null);
    }

    /**
     * Add a map marker for a comet to the specified world (only visible to owner)
     * 
     * @param world     The world
     * @param blockPos  The comet block position
     * @param tier      The comet tier
     * @param ownerUUID The UUID of the player who owns this comet (marker only
     *                  visible to them)
     */
    private void addCometMapMarker(com.hypixel.hytale.server.core.universe.world.World world, Vector3i blockPos,
            CometTier tier, java.util.UUID ownerUUID) {
        try {
            if (world == null) {
                LOGGER.warning("Cannot add map marker: world is null");
                return;
            }

            // Create marker ID
            String markerId = "Comet-" + blockPos.x + "," + blockPos.y + "," + blockPos.z;

            // Create marker name
            String markerName = "Comet (" + tier.getName() + ")";

            // Get icon path - icons are in Common/UI/WorldMap/MapMarkers/
            String iconPath = "Comet_Stone_" + tier.getName() + ".png";

            // Convert block position to world position (center of block)
            Vector3d markerPos = blockPos.toVector3d();

            // Create Transform using PROTOCOL classes (not math.vector) - this is what
            // MapTrail uses!
            com.hypixel.hytale.protocol.Position position = new com.hypixel.hytale.protocol.Position(markerPos.x,
                    markerPos.y, markerPos.z);
            com.hypixel.hytale.protocol.Direction direction = new com.hypixel.hytale.protocol.Direction(); // Zero
                                                                                                           // rotation
            com.hypixel.hytale.protocol.Transform transform = new com.hypixel.hytale.protocol.Transform(position,
                    direction);

            // Create the MapMarker
            com.hypixel.hytale.protocol.packets.worldmap.MapMarker marker = new com.hypixel.hytale.protocol.packets.worldmap.MapMarker(
                    markerId,
                    markerName,
                    iconPath,
                    transform, // Use protocol Transform directly, not toTransformPacket!
                    null // No context menu items
            );

            // DON'T add to world's global points of interest - that makes it visible to
            // everyone
            // Instead, only send the marker to the owner player directly
            // world.getWorldMapManager().getPointsOfInterest().put(markerId, marker);

            // Check if globalComets is enabled - if so, show markers to all players
            CometConfig config = CometConfig.getInstance();
            boolean globalComets = (config != null && config.globalComets);

            // Send UpdateWorldMap packet - to owner only, or to all if globalComets is enabled
            sendMarkerToOwner(world, marker, globalComets ? null : ownerUUID);
        } catch (Exception e) {
            LOGGER.warning("Failed to add comet map marker to world " + (world != null ? world.getName() : "null")
                    + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send a marker only to the owner player via UpdateWorldMap packet
     * 
     * @param world     The world
     * @param marker    The marker to send
     * @param ownerUUID The UUID of the owner player (null = send to all players)
     */
    private void sendMarkerToOwner(com.hypixel.hytale.server.core.universe.world.World world,
            com.hypixel.hytale.protocol.packets.worldmap.MapMarker marker,
            java.util.UUID ownerUUID) {
        try {
            com.hypixel.hytale.protocol.packets.worldmap.MapMarker[] markersToAdd = new com.hypixel.hytale.protocol.packets.worldmap.MapMarker[] {
                    marker };
            com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap updatePacket = new com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap(
                    null, markersToAdd, null);

            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                try {
                    // Only send to the owner, or to all if ownerUUID is null
                    if (ownerUUID == null || player.getUuid().equals(ownerUUID)) {
                        player.getPlayerConnection().writeNoCache(updatePacket);
                        if (ownerUUID != null) {
                            LOGGER.info("Sent comet marker to owner: " + ownerUUID);
                        }
                    }
                } catch (Exception e) {
                    // Silently fail - player might have disconnected
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send comet marker to owner: " + e.getMessage());
        }
    }

    /**
     * Remove map marker for a comet when it's broken
     * Sends removal packet only to the owner player
     */
    public void removeCometMapMarker(com.hypixel.hytale.server.core.universe.world.World world, Vector3i blockPos) {
        try {
            if (world == null) {
                return;
            }

            // Get the owner UUID before removing from tracking
            java.util.UUID ownerUUID = cometOwners.get(blockPos);

            String markerId = "Comet-" + blockPos.x + "," + blockPos.y + "," + blockPos.z;
            // We're not using global POI anymore, but remove just in case
            world.getWorldMapManager().getPointsOfInterest().remove(markerId);

            // Check if globalComets is enabled
            CometConfig config = CometConfig.getInstance();
            boolean globalComets = (config != null && config.globalComets);

            // Send removal packet only to owner player (or all if globalComets or no owner)
            String[] markersToRemove = new String[] { markerId };
            com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap updatePacket = new com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap(
                    null, null, markersToRemove);

            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                try {
                    // Only send to the owner, or to all if globalComets is enabled or ownerUUID is null
                    if (globalComets || ownerUUID == null || player.getUuid().equals(ownerUUID)) {
                        player.getPlayerConnection().writeNoCache(updatePacket);
                        LOGGER.info("Sent comet marker removal to player: " + player.getUuid() + " for comet at "
                                + blockPos);
                    }
                } catch (Exception e) {
                    // Silently fail - player might have disconnected
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to remove comet map marker: " + e.getMessage());
        }
    }

    /**
     * Handle block break when we have a Store (e.g. from BreakBlockEvent). Cleans
     * up tracking and removes the map marker.
     */
    public void handleBlockBreak(Store<EntityStore> store, Vector3i blockPos) {
        // Remove map marker first (needs ownerUUID which is still in cometOwners)
        try {
            com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                    .getExternalData()).getWorld();
            removeCometMapMarker(world, blockPos);
        } catch (Exception e) {
            LOGGER.warning("Failed to get world for map marker removal: " + e.getMessage());
        }
        // Remove from active comets and tracking
        activeComets.remove(blockPos);
        cometTiers.remove(blockPos);
        cometOwners.remove(blockPos);
    }

    /**
     * Handle block break when we have World but no Store (e.g. comet despawns due
     * to timer in CometDespawnTracker or CometFallingSystem).
     * Removes the map marker and all tracking so the marker disappears and
     * CometMarkerProvider stops including it.
     */
    public void handleBlockBreak(com.hypixel.hytale.server.core.universe.world.World world, Vector3i blockPos) {
        if (world == null)
            return;
        removeCometMapMarker(world, blockPos);
        activeComets.remove(blockPos);
        cometTiers.remove(blockPos);
        cometOwners.remove(blockPos);
        forcedThemes.remove(blockPos);
    }

    /**
     * Set a forced theme for a specific comet block (string-based)
     * 
     * @param blockPos The block position
     * @param themeId  The theme ID (string)
     */
    public void forceTheme(Vector3i blockPos, String themeId) {
        forcedThemes.put(blockPos, themeId);
        LOGGER.info("Forced theme '" + themeId + "' for comet at " + blockPos);
    }

    /**
     * Set a forced theme for a specific comet block (legacy int-based, for
     * backwards compatibility)
     * 
     * @param blockPos   The block position
     * @param themeIndex The legacy theme index
     */
    public void forceThemeLegacy(Vector3i blockPos, int themeIndex) {
        String themeId = getLegacyThemeId(themeIndex);
        forcedThemes.put(blockPos, themeId);
        LOGGER.info("Forced legacy theme " + themeIndex + " (" + themeId + ") for comet at " + blockPos);
    }

    /**
     * Get theme ID from name (case insensitive) - now returns string
     * 
     * @param name Theme name
     * @return Theme ID (string), or null if not found
     */
    public String getThemeIdByName(String name) {
        return WaveThemeProvider.findThemeByName(name);
    }

    /**
     * Get legacy theme ID from name (for backwards compatibility with
     * CometSpawnCommand)
     * 
     * @param name Theme name
     * @return Theme ID (int), or -1 if not found
     */
    public int getThemeId(String name) {
        String themeId = WaveThemeProvider.findThemeByName(name);
        if (themeId != null) {
            return getLegacyThemeIndex(themeId);
        }

        // Fallback to legacy lookup
        if (name == null || name.isEmpty())
            return -1;

        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (THEME_NAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }

        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (THEME_NAMES[i].toLowerCase().contains(name.toLowerCase())) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get all valid theme names (from config if available, else legacy)
     * 
     * @return Array of theme names
     */
    public String[] getThemeNames() {
        String[] configNames = WaveThemeProvider.getAllThemeNames();
        if (configNames != null && configNames.length > 0) {
            return configNames;
        }
        return THEME_NAMES;
    }

    public void handleMobDeath(
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> mobRef) {
        LOGGER.info("[CometWaveManager] handleMobDeath called! Checking " + activeWaves.size() + " active waves...");

        // Check all active waves to see if this mob belongs to any of them
        for (Map.Entry<com.hypixel.hytale.math.vector.Vector3i, WaveData> entry : activeWaves.entrySet()) {
            WaveData waveData = entry.getValue();
            LOGGER.info("[CometWaveManager] Checking wave at " + entry.getKey() + " with " + waveData.spawnedMobs.size()
                    + " mobs in list");

            // Check if this mob is in the list (by reference or by checking if ref matches)
            boolean found = false;
            for (int i = waveData.spawnedMobs.size() - 1; i >= 0; i--) {
                Ref<EntityStore> ref = waveData.spawnedMobs.get(i);
                // Check if refs match (same entity) - use == for reference equality or check if
                // they point to same entity
                if (ref != null && (ref == mobRef || ref.equals(mobRef) ||
                        (ref.isValid() && mobRef.isValid() && ref.getIndex() == mobRef.getIndex()))) {
                    found = true;
                    waveData.spawnedMobs.remove(i);
                    LOGGER.info("[CometWaveManager] Mob died for wave at " + entry.getKey() + " (removed from list, " +
                            waveData.spawnedMobs.size() + " remaining)");
                    break;
                }
            }

            if (found) {
                // Update countdown after mob death. Use player's store if valid, else mob's
                // (e.g. player dead/DC) so completion and loot still run when boss is killed.
                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = waveData.playerRef != null
                        && waveData.playerRef.isValid()
                                ? waveData.playerRef.getStore()
                                : mobRef.getStore();
                updateWaveCountdown(store, waveData.playerRef, waveData);
                return; // Found and handled, exit
            }
        }

        LOGGER.info("[CometWaveManager] Mob death not found in any active wave");
    }

    public void cleanup() {
        activeComets.clear();
        activeWaves.clear();
        cometTiers.clear();
        cometOwners.clear();
        cometThemes.clear();
    }

    // ========== LEGACY FALLBACK METHODS ==========
    // These methods provide backwards compatibility when config is not available

    /**
     * Convert legacy integer theme index to string theme ID
     */
    private String getLegacyThemeId(int legacyIndex) {
        String[] legacyIds = {
                "skeleton", "goblin", "spider", "trork", "skeleton_sand", "sabertooth",
                "outlander", "leopard", "toad", "skeleton_burnt", "void", "ice",
                "burnt_legendary", "lava", "earth", "undead_rare", "undead_legendary", "zombie"
        };
        if (legacyIndex >= 0 && legacyIndex < legacyIds.length) {
            return legacyIds[legacyIndex];
        }
        return "skeleton"; // Default fallback
    }

    /**
     * Convert string theme ID to legacy integer index
     */
    private int getLegacyThemeIndex(String themeId) {
        if (themeId == null)
            return 0;
        String[] legacyIds = {
                "skeleton", "goblin", "spider", "trork", "skeleton_sand", "sabertooth",
                "outlander", "leopard", "toad", "skeleton_burnt", "void", "ice",
                "burnt_legendary", "lava", "earth", "undead_rare", "undead_legendary", "zombie"
        };
        for (int i = 0; i < legacyIds.length; i++) {
            if (legacyIds[i].equals(themeId)) {
                return i;
            }
        }
        return -1; // Not found
    }

    /**
     * Legacy theme selection (fallback when config fails)
     */
    private int selectThemeLegacy(CometTier tier) {
        List<Integer> availableThemes = new ArrayList<>();
        int cometTierNum = getTierIndex(tier) + 1;

        for (int theme = 0; theme < THEME_NATIVE_TIER.length; theme++) {
            int themeNativeTier = THEME_NATIVE_TIER[theme];
            if (theme == THEME_VOID) {
                if (cometTierNum < 4)
                    availableThemes.add(theme);
            } else if (Math.abs(themeNativeTier - cometTierNum) <= 1) {
                availableThemes.add(theme);
            }
        }

        if (availableThemes.isEmpty()) {
            return THEME_SKELETON;
        }
        return availableThemes.get(RANDOM.nextInt(availableThemes.size()));
    }

    /**
     * Legacy mob list (fallback when config fails)
     */
    private String[] getMobListForThemeLegacy(CometTier tier, int theme) {
        String[] baseMobs = getBaseMobsForTheme(theme, tier);
        if (baseMobs == null) {
            return null;
        }
        String[] tieredMobs = new String[baseMobs.length];
        for (int i = 0; i < baseMobs.length; i++) {
            tieredMobs[i] = applyTierSuffix(baseMobs[i], tier);
        }
        return tieredMobs;
    }

    /**
     * Legacy boss list (fallback when config fails)
     */
    private java.util.List<String> getBossesLegacy(CometTier tier, int theme) {
        if (theme == THEME_OUTLANDER && tier == CometTier.LEGENDARY) {
            return java.util.Arrays.asList(applyTierSuffix("Werewolf", tier), applyTierSuffix("Yeti", tier));
        }
        return java.util.Collections.singletonList(getBossForTierAndTheme(tier, theme));
    }
}
