package com.cometmod;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks comet spawn times for persistent despawn across server restarts.
 * Saves data to JSON file and loads on startup.
 */
public class CometDespawnTracker {
    
    private static final Logger LOGGER = Logger.getLogger("CometDespawnTracker");
    private static final String DATA_FILE_NAME = "comet_despawns.json";
    
    // Track active comets: position -> spawn timestamp (milliseconds since epoch)
    private final Map<String, Long> cometSpawnTimes = new ConcurrentHashMap<>();

    // Track comet tiers for logging
    private final Map<String, String> cometTiers = new ConcurrentHashMap<>();

    // Track custom despawn times per comet (null = use global)
    private final Map<String, Double> customDespawnTimes = new ConcurrentHashMap<>();
    
    // Flag to prevent processing multiple times
    private boolean hasProcessedStartup = false;
    
    private static CometDespawnTracker instance;
    
    public static CometDespawnTracker getInstance() {
        if (instance == null) {
            LOGGER.info("[TRACKER] Creating new CometDespawnTracker instance...");
            instance = new CometDespawnTracker();
        }
        return instance;
    }
    
    private CometDespawnTracker() {
        LOGGER.info("[TRACKER] CometDespawnTracker constructor called");
        // Load existing data on creation
        load();
        LOGGER.info("[TRACKER] Loaded " + cometSpawnTimes.size() + " comets from file");
    }
    
    /**
     * Convert Vector3i to string key for map storage
     */
    private String posToKey(Vector3i pos) {
        return pos.x + "," + pos.y + "," + pos.z;
    }
    
    /**
     * Convert string key back to Vector3i
     */
    private Vector3i keyToPos(String key) {
        String[] parts = key.split(",");
        return new Vector3i(
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }
    
    /**
     * Register a new comet that just spawned
     */
    public void registerComet(Vector3i pos, String tierName) {
        registerComet(pos, tierName, null);
    }

    /**
     * Register a new comet with custom despawn time
     * @param pos The comet position
     * @param tierName The tier name for logging
     * @param customDespawnMinutes Custom despawn time in minutes (null = use global)
     */
    public void registerComet(Vector3i pos, String tierName, Double customDespawnMinutes) {
        String key = posToKey(pos);
        long spawnTime = System.currentTimeMillis();
        cometSpawnTimes.put(key, spawnTime);
        cometTiers.put(key, tierName);
        if (customDespawnMinutes != null) {
            customDespawnTimes.put(key, customDespawnMinutes);
            LOGGER.info("Registered comet at " + pos + " (tier: " + tierName + ") spawn time: " + spawnTime + " custom despawn: " + customDespawnMinutes + " min");
        } else {
            customDespawnTimes.remove(key);
            LOGGER.info("Registered comet at " + pos + " (tier: " + tierName + ") spawn time: " + spawnTime);
        }
        save();
    }
    
    /**
     * Unregister a comet (was broken or despawned)
     */
    public void unregisterComet(Vector3i pos) {
        String key = posToKey(pos);
        if (cometSpawnTimes.remove(key) != null) {
            cometTiers.remove(key);
            customDespawnTimes.remove(key);
            LOGGER.info("Unregistered comet at " + pos);
            save();
        }
    }

    /**
     * Get the despawn time for a specific comet in minutes (custom or global)
     */
    public double getDespawnTimeForComet(Vector3i pos) {
        String key = posToKey(pos);
        Double custom = customDespawnTimes.get(key);
        if (custom != null) {
            return custom;
        }
        return CometFallingSystem.getDespawnTimeMinutes();
    }
    
    /**
     * Check if a comet is registered
     */
    public boolean isRegistered(Vector3i pos) {
        return cometSpawnTimes.containsKey(posToKey(pos));
    }
    
    /**
     * Get remaining despawn time in milliseconds for a comet
     * Returns 0 or negative if already expired
     */
    public long getRemainingTime(Vector3i pos, double despawnMinutes) {
        String key = posToKey(pos);
        Long spawnTime = cometSpawnTimes.get(key);
        if (spawnTime == null) return -1;
        
        long despawnMs = (long)(despawnMinutes * 60 * 1000);
        long despawnAt = spawnTime + despawnMs;
        return despawnAt - System.currentTimeMillis();
    }
    
    /**
     * Get all registered comet positions
     */
    public Set<Vector3i> getAllPositions() {
        Set<Vector3i> positions = new HashSet<>();
        for (String key : cometSpawnTimes.keySet()) {
            positions.add(keyToPos(key));
        }
        return positions;
    }
    
    /**
     * Process all comets on startup - despawn expired ones, reschedule remaining
     */
    public void processOnStartup(World world, double despawnMinutes) {
        if (hasProcessedStartup) {
            LOGGER.info("[STARTUP] Already processed startup, skipping...");
            return;
        }
        
        LOGGER.info("[STARTUP] ===== Processing " + cometSpawnTimes.size() + " tracked comets on startup =====");
        LOGGER.info("[STARTUP] Despawn time: " + despawnMinutes + " minutes (" + (despawnMinutes * 60) + " seconds)");
        LOGGER.info("[STARTUP] Current time: " + System.currentTimeMillis());
        LOGGER.info("[STARTUP] World: " + (world != null ? world.getName() : "NULL"));
        
        if (cometSpawnTimes.isEmpty()) {
            LOGGER.info("[STARTUP] No comets to process");
            hasProcessedStartup = true;
            return;
        }
        
        // Copy keys to avoid concurrent modification
        List<String> keys = new ArrayList<>(cometSpawnTimes.keySet());
        
        for (String key : keys) {
            Vector3i pos = keyToPos(key);
            Long spawnTime = cometSpawnTimes.get(key);
            String tier = cometTiers.getOrDefault(key, "Unknown");
            
            LOGGER.info("[STARTUP] Checking comet at " + pos + " (tier: " + tier + ", spawn time: " + spawnTime + ")");
            
            long remaining = getRemainingTime(pos, despawnMinutes);
            long despawnAt = spawnTime + (long)(despawnMinutes * 60 * 1000);
            
            LOGGER.info("[STARTUP]   - Spawn time: " + spawnTime);
            LOGGER.info("[STARTUP]   - Despawn at: " + despawnAt);
            LOGGER.info("[STARTUP]   - Remaining: " + remaining + "ms (" + (remaining/1000) + "s)");
            
            if (remaining <= 0) {
                // Expired - should despawn immediately
                // Schedule with 2 second delay to ensure world is fully loaded
                long expiredBy = -remaining;
                LOGGER.info("[STARTUP]   - STATUS: EXPIRED (was due " + (expiredBy/1000) + "s ago), scheduling immediate removal...");
                final Vector3i finalPos = pos;
                com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    world.execute(() -> {
                        try {
                            LOGGER.info("[STARTUP] Executing delayed despawn for expired comet at " + finalPos);
                            despawnCometBlock(world, finalPos);
                            unregisterComet(finalPos);
                        } catch (Exception e) {
                            LOGGER.warning("[STARTUP] Error despawning expired comet at " + finalPos + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }, 2, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // Still has time - reschedule
                LOGGER.info("[STARTUP]   - STATUS: ACTIVE (" + (remaining/1000) + "s remaining), rescheduling despawn...");
                scheduleDespawn(world, pos, remaining);
            }
        }
        
        hasProcessedStartup = true;
        LOGGER.info("[STARTUP] ===== Startup processing complete =====");
        
        // Start periodic cleanup check (every 5 minutes)
        startPeriodicCleanup(world, despawnMinutes);
    }
    
    /**
     * Start periodic cleanup task that runs every 5 minutes to catch any missed comets
     */
    private void startPeriodicCleanup(World world, double despawnMinutes) {
        LOGGER.info("[PERIODIC] Starting periodic cleanup task (every 5 minutes)");
        
        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                world.execute(() -> {
                    try {
                        checkAndDespawnExpired(world, despawnMinutes);
                    } catch (Exception e) {
                        LOGGER.warning("[PERIODIC] Error in periodic cleanup: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                LOGGER.warning("[PERIODIC] Error scheduling periodic cleanup: " + e.getMessage());
            }
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }
    
    /**
     * Check all tracked comets and despawn any that have expired
     */
    private void checkAndDespawnExpired(World world, double despawnMinutes) {
        if (cometSpawnTimes.isEmpty()) {
            return; // Nothing to check
        }
        
        LOGGER.info("[PERIODIC] Checking " + cometSpawnTimes.size() + " tracked comets for expiration...");
        
        // Copy keys to avoid concurrent modification
        List<String> keys = new ArrayList<>(cometSpawnTimes.keySet());
        List<Vector3i> expired = new ArrayList<>();
        
        for (String key : keys) {
            Vector3i pos = keyToPos(key);
            long remaining = getRemainingTime(pos, despawnMinutes);
            
            if (remaining <= 0) {
                // Expired - despawn it
                long expiredBy = -remaining;
                LOGGER.info("[PERIODIC] Found expired comet at " + pos + " (expired " + (expiredBy/1000) + "s ago)");
                expired.add(pos);
            }
        }
        
        // Despawn all expired comets
        for (Vector3i pos : expired) {
            try {
                LOGGER.info("[PERIODIC] Despawning expired comet at " + pos);
                despawnCometBlock(world, pos);
                unregisterComet(pos);
            } catch (Exception e) {
                LOGGER.warning("[PERIODIC] Error despawning comet at " + pos + ": " + e.getMessage());
            }
        }
        
        if (!expired.isEmpty()) {
            LOGGER.info("[PERIODIC] Cleaned up " + expired.size() + " expired comet(s)");
        } else {
            LOGGER.info("[PERIODIC] All comets are still active");
        }
    }
    
    /**
     * Schedule a despawn for a comet with remaining time
     */
    private void scheduleDespawn(World world, Vector3i pos, long remainingMs) {
        long remainingSeconds = Math.max(1, remainingMs / 1000);
        
        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            world.execute(() -> {
                try {
                    despawnCometBlock(world, pos);
                    unregisterComet(pos);
                } catch (Exception e) {
                    LOGGER.warning("Error despawning comet at " + pos + ": " + e.getMessage());
                }
            });
        }, remainingSeconds, java.util.concurrent.TimeUnit.SECONDS);
        
        LOGGER.info("Scheduled despawn for comet at " + pos + " in " + remainingSeconds + " seconds");
    }
    
    /**
     * Despawn a comet block from the world
     */
    private void despawnCometBlock(World world, Vector3i pos) {
        LOGGER.info("[DESPAWN] Starting despawn process for comet at " + pos);
        try {
            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            LOGGER.info("[DESPAWN] Chunk index: " + chunkIndex);
            
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) {
                LOGGER.info("[DESPAWN] Chunk not in memory, loading...");
                chunk = world.getChunk(chunkIndex);
            }
            
            if (chunk == null) {
                LOGGER.warning("[DESPAWN] Could not get chunk for despawn at " + pos);
                return;
            }
            
            LOGGER.info("[DESPAWN] Got chunk, checking block type...");
            
            // Check if block is still a comet
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = 
                chunk.getBlockType(pos.x, pos.y, pos.z);
            
            if (blockType == null) {
                LOGGER.warning("[DESPAWN] Block type is NULL at " + pos);
                return;
            }
            
            String blockId = blockType.getId();
            LOGGER.info("[DESPAWN] Block type at " + pos + ": " + blockId);
            
            if (blockId.contains("Comet_Stone")) {
                LOGGER.info("[DESPAWN] Block is a comet, attempting to break...");
                boolean broken = world.breakBlock(pos.x, pos.y, pos.z, 0);
                LOGGER.info("[DESPAWN] breakBlock returned: " + broken);
                
                if (broken) {
                    chunk.markNeedsSaving();
                    LOGGER.info("[DESPAWN] SUCCESS: Despawned comet block at " + pos);
                } else {
                    LOGGER.warning("[DESPAWN] FAILED: breakBlock returned false for " + pos);
                }
            } else {
                LOGGER.info("[DESPAWN] Block at " + pos + " is not a comet (type: " + blockId + "), skipping despawn");
            }
            
            // Clean up wave manager tracking and remove map marker (world available; store not)
            CometWaveManager waveManager = CometModPlugin.getWaveManager();
            if (waveManager != null) {
                waveManager.handleBlockBreak(world, pos);
                LOGGER.info("[DESPAWN] Cleaned up wave manager tracking and removed map marker");
            }
            
        } catch (Exception e) {
            LOGGER.warning("[DESPAWN] ERROR despawning comet: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the data file path
     */
    private File getDataFile() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File modFolder = new File(appData + File.separator + "Hytale" + File.separator + 
                                         "UserData" + File.separator + "Mods" + File.separator + "CometMod");
                if (!modFolder.exists()) {
                    modFolder.mkdirs();
                }
                return new File(modFolder, DATA_FILE_NAME);
            }
        } catch (Exception e) {
            LOGGER.warning("Error getting data file path: " + e.getMessage());
        }
        
        // Fallback to current directory
        return new File(DATA_FILE_NAME);
    }
    
    /**
     * Save comet data to JSON file
     */
    public void save() {
        File dataFile = getDataFile();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(dataFile))) {
            writer.println("{");
            writer.println("  \"comets\": [");
            
            int count = 0;
            int total = cometSpawnTimes.size();
            
            for (Map.Entry<String, Long> entry : cometSpawnTimes.entrySet()) {
                String key = entry.getKey();
                Long spawnTime = entry.getValue();
                String tier = cometTiers.getOrDefault(key, "Unknown");
                String[] parts = key.split(",");
                
                writer.print("    {\"x\":" + parts[0] + ",\"y\":" + parts[1] + ",\"z\":" + parts[2] + 
                           ",\"spawnTime\":" + spawnTime + ",\"tier\":\"" + tier + "\"}");
                
                count++;
                if (count < total) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            
            writer.println("  ]");
            writer.println("}");
            
            LOGGER.info("Saved " + total + " comet entries to " + dataFile.getAbsolutePath());
            
        } catch (Exception e) {
            LOGGER.warning("Failed to save comet data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load comet data from JSON file
     */
    public void load() {
        LOGGER.info("[TRACKER] Loading comet despawn data...");
        File dataFile = getDataFile();
        LOGGER.info("[TRACKER] Data file path: " + dataFile.getAbsolutePath());
        
        if (!dataFile.exists()) {
            LOGGER.info("[TRACKER] No comet data file found at " + dataFile.getAbsolutePath() + ", starting fresh");
            return;
        }
        
        LOGGER.info("[TRACKER] Data file exists, reading...");
        
        try {
            String content = new String(Files.readAllBytes(dataFile.toPath()));
            
            // Simple JSON parsing for our format
            int cometsStart = content.indexOf("[");
            int cometsEnd = content.lastIndexOf("]");
            
            if (cometsStart == -1 || cometsEnd == -1) {
                LOGGER.warning("Invalid comet data file format");
                return;
            }
            
            String cometsArray = content.substring(cometsStart + 1, cometsEnd).trim();
            
            if (cometsArray.isEmpty()) {
                LOGGER.info("No comets in data file");
                return;
            }
            
            // Parse each comet entry
            int loaded = 0;
            int startIdx = 0;
            
            while (true) {
                int objStart = cometsArray.indexOf("{", startIdx);
                if (objStart == -1) break;
                
                int objEnd = cometsArray.indexOf("}", objStart);
                if (objEnd == -1) break;
                
                String obj = cometsArray.substring(objStart + 1, objEnd);
                
                try {
                    int x = parseIntValue(obj, "x");
                    int y = parseIntValue(obj, "y");
                    int z = parseIntValue(obj, "z");
                    long spawnTime = parseLongValue(obj, "spawnTime");
                    String tier = parseStringValue(obj, "tier");
                    
                    String key = x + "," + y + "," + z;
                    cometSpawnTimes.put(key, spawnTime);
                    cometTiers.put(key, tier);
                    loaded++;
                    
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse comet entry: " + obj + " - " + e.getMessage());
                }
                
                startIdx = objEnd + 1;
            }
            
            LOGGER.info("Loaded " + loaded + " comet entries from " + dataFile.getAbsolutePath());
            
        } catch (Exception e) {
            LOGGER.warning("Failed to load comet data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int parseIntValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) throw new RuntimeException("Key not found: " + key);
        
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Integer.parseInt(json.substring(start, end).trim());
    }
    
    private long parseLongValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) throw new RuntimeException("Key not found: " + key);
        
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
    
    private String parseStringValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "Unknown";
        
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "Unknown";
        
        return json.substring(start, end);
    }
}
