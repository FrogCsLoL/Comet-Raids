package com.cometmod;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.cometmod.config.ZoneSpawnChances;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

public class CometSpawnTask {
    
    private static final Logger LOGGER = Logger.getLogger("CometSpawnTask");
    
    // Retry configuration for world thread initialization
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 100L; // Start with 100ms delay
    
    // Spawn timing configuration
    private int minDelaySeconds = 120;  // 2 minutes minimum
    private int maxDelaySeconds = 300;  // 5 minutes maximum
    private double spawnChance = 0.4;   // 40% chance to spawn each check
    private int minSpawnDistance = 30;  // Minimum distance from player (blocks)
    private int maxSpawnDistance = 50;  // Maximum distance from player (blocks)
    
    // Getters and setters for UI configuration
    public int getMinDelaySeconds() { return minDelaySeconds; }
    public void setMinDelaySeconds(int minDelaySeconds) {
        this.minDelaySeconds = minDelaySeconds;
    }
    
    public int getMaxDelaySeconds() { return maxDelaySeconds; }
    public void setMaxDelaySeconds(int maxDelaySeconds) {
        this.maxDelaySeconds = maxDelaySeconds;
    }
    
    public double getSpawnChance() { return spawnChance; }
    public void setSpawnChance(double spawnChance) {
        this.spawnChance = Math.max(0.0, Math.min(1.0, spawnChance));
    }
    
    public int getMinSpawnDistance() { return minSpawnDistance; }
    public void setMinSpawnDistance(int minSpawnDistance) {
        this.minSpawnDistance = minSpawnDistance;
    }
    
    public int getMaxSpawnDistance() { return maxSpawnDistance; }
    public void setMaxSpawnDistance(int maxSpawnDistance) {
        this.maxSpawnDistance = maxSpawnDistance;
    }
    
    private final Set<Player> trackedPlayers = new HashSet<>();
    private ScheduledFuture<?> future;
    private final World world;
    private final Store<EntityStore> store;
    
    public CometSpawnTask(World world, Store<EntityStore> store) {
        this.world = world;
        this.store = store;
    }
    
    public void addPlayer(Player player) {
        synchronized (trackedPlayers) {
            trackedPlayers.add(player);
        }
    }

    public void removePlayer(Player player) {
        synchronized (trackedPlayers) {
            trackedPlayers.remove(player);
        }
    }

    public void setDelayRangeSeconds(int min, int max) {
        this.minDelaySeconds = min;
        this.maxDelaySeconds = max;
    }
    
    // Legacy getters (for compatibility)
    public int getMinSeconds() {
        return minDelaySeconds;
    }
    
    public int getMaxSeconds() {
        return maxDelaySeconds;
    }
    
    public void start() {
        if (future != null && !future.isCancelled()) {
            return;
        }
        scheduleNextSpawn();
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public ScheduledFuture<?> getFuture() {
        return future;
    }

    private void scheduleNextSpawn() {
        Random random = new Random();
        long delaySeconds = minDelaySeconds + random.nextInt(maxDelaySeconds - minDelaySeconds + 1);
        
        future = com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(
            this::checkAndSpawn,
            delaySeconds,
            java.util.concurrent.TimeUnit.SECONDS
        );
    }
    
    private void checkAndSpawn() {
        try {
            // Check if natural spawns are enabled
            CometConfig config = CometConfig.getInstance();
            if (config != null && !config.naturalSpawnsEnabled) {
                scheduleNextSpawn();
                return;
            }

            Random random = new Random();

            if (random.nextDouble() > spawnChance) {
                scheduleNextSpawn();
                return;
            }

            List<Player> playersToCheck;
            synchronized (trackedPlayers) {
                if (trackedPlayers.isEmpty()) {
                    scheduleNextSpawn();
                    return;
                }
                playersToCheck = new ArrayList<>(trackedPlayers);
            }

            if (!playersToCheck.isEmpty()) {
                Player targetPlayer = playersToCheck.get(random.nextInt(playersToCheck.size()));
                spawnForPlayer(targetPlayer);
            }

            scheduleNextSpawn();
            
        } catch (Exception e) {
            LOGGER.warning("Error in comet spawn check: " + e.getMessage());
            e.printStackTrace();
            // Schedule next spawn even on error
            scheduleNextSpawn();
        }
    }
    
    public void spawnForPlayer(Player player) {
        spawnForPlayerWithRetry(player, 0);
    }

    private void spawnForPlayerWithRetry(Player player, int attempt) {
        try {
            // Get player's current zone
            WorldMapTracker tracker = player.getWorldMapTracker();
            WorldMapTracker.ZoneDiscoveryInfo zoneInfo = tracker != null ? tracker.getCurrentZone() : null;
            
            String zoneName = zoneInfo != null ? zoneInfo.zoneName() : null;
            String regionName = zoneInfo != null ? zoneInfo.regionName() : null;
            
            // Parse zone ID from region name first (e.g., "Zone4_Tier4" -> 4), then fallback to zone name
            int zoneId = parseZoneId(regionName);
            if (zoneId == 0 && zoneName != null) {
                zoneId = parseZoneId(zoneName);
            }
            
            CometTier tier = selectTierForZone(zoneId);
            World currentWorld = player.getWorld();
            if (currentWorld == null) {
                LOGGER.warning("Player " + player.getDisplayName() + " is not in any world, cannot spawn comet");
                return;
            }
            
            // Try to execute spawn logic on world thread with retry mechanism
            try {
                currentWorld.execute(() -> {
                    executeSpawnLogic(player, tier);
                });
                // Success! No retry needed
            } catch (Exception e) {
                // Check if this is an IllegalThreadStateException (may be wrapped)
                Throwable cause = e;
                boolean isThreadStateException = false;
                
                // Check exception and its cause chain
                while (cause != null) {
                    if (cause instanceof java.lang.IllegalThreadStateException) {
                        isThreadStateException = true;
                        break;
                    }
                    cause = cause.getCause();
                }
                
                // Also check exception message as fallback (in case exception is wrapped differently)
                if (!isThreadStateException && e.getMessage() != null && 
                    e.getMessage().contains("World thread is not accepting tasks")) {
                    isThreadStateException = true;
                }
                
                if (isThreadStateException) {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        long delayMs = INITIAL_RETRY_DELAY_MS * (1L << attempt);
                        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(
                            () -> spawnForPlayerWithRetry(player, attempt + 1),
                            delayMs,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        );
                    }
                } else {
                    LOGGER.warning("Error spawning comet: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error in spawnForPlayer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void executeSpawnLogic(Player player, CometTier tier) {
        try {
            World currentWorld = player.getWorld();
            if (currentWorld == null) return;

            Store<EntityStore> currentStore = currentWorld.getEntityStore().getStore();
            if (currentStore == null) return;
            
            com.hypixel.hytale.component.Ref<EntityStore> playerRef = player.getReference();
            if (playerRef == null || !playerRef.isValid()) return;

            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                currentStore.getComponent(playerRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) return;

            com.hypixel.hytale.math.vector.Vector3d playerPos = transform.getPosition();
            Random random = new Random();
            com.hypixel.hytale.math.vector.Vector3i targetBlockPos = null;

            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = minSpawnDistance + random.nextDouble() * (maxSpawnDistance - minSpawnDistance);

                int spawnX = (int)(playerPos.x + Math.cos(angle) * distance);
                int spawnZ = (int)(playerPos.z + Math.sin(angle) * distance);
                int spawnY = findGroundLevel(currentWorld, spawnX, spawnZ, (int)playerPos.y);

                if (spawnY == -1) continue;
                if (isInWater(currentWorld, spawnX, spawnY, spawnZ) || isInWater(currentWorld, spawnX, spawnY + 1, spawnZ)) continue;

                targetBlockPos = new com.hypixel.hytale.math.vector.Vector3i(spawnX, spawnY + 1, spawnZ);
                break;
            }

            if (targetBlockPos == null) return;
            
            CometFallingSystem fallingSystem = CometModPlugin.getFallingSystem();
            if (fallingSystem == null) {
                fallingSystem = new CometFallingSystem(currentWorld);
                CometModPlugin.setFallingSystem(fallingSystem);
            }

            com.hypixel.hytale.component.CommandBuffer<EntityStore> commandBuffer = null;
            try {
                java.lang.reflect.Method takeCommandBufferMethod = currentStore.getClass().getDeclaredMethod("takeCommandBuffer");
                takeCommandBufferMethod.setAccessible(true);
                commandBuffer = (com.hypixel.hytale.component.CommandBuffer<EntityStore>) takeCommandBufferMethod.invoke(currentStore);
            } catch (Exception e) {
                return;
            }

            java.util.UUID ownerUUID = player.getUuid();
            fallingSystem.spawnFallingComet(playerRef, targetBlockPos, tier, null, currentStore, currentWorld, ownerUUID);

            try {
                java.lang.reflect.Method consumeMethod = commandBuffer.getClass().getDeclaredMethod("consume");
                consumeMethod.setAccessible(true);
                consumeMethod.invoke(commandBuffer);
            } catch (Exception e) {
                // Ignore
            }
            
            try {
                com.hypixel.hytale.server.core.Message coordMessage =
                    com.hypixel.hytale.server.core.Message.raw(
                        tier.getName() + " Comet falling! Target: X=" + targetBlockPos.x +
                        ", Y=" + targetBlockPos.y +
                        ", Z=" + targetBlockPos.z
                    );
                player.sendMessage(coordMessage);
            } catch (Exception e) {
                // Ignore
            }

            try {
                com.hypixel.hytale.server.core.universe.PlayerRef playerRefComponent =
                    currentStore.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRefComponent != null) {
                    com.hypixel.hytale.server.core.Message primaryTitle =
                        com.hypixel.hytale.server.core.Message.raw(tier.getName() + " Comet Falling!");
                    com.hypixel.hytale.server.core.Message secondaryTitle =
                        com.hypixel.hytale.server.core.Message.raw("Watch the sky!");

                    com.hypixel.hytale.server.core.util.EventTitleUtil.showEventTitleToPlayer(
                        playerRefComponent, primaryTitle, secondaryTitle,
                        true, null, 3.0F, 0.1F, 0.5F
                    );

                    com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        try {
                            World playerWorld = player.getWorld();
                            if (playerWorld != null) {
                                playerWorld.execute(() -> {
                                    com.hypixel.hytale.server.core.util.EventTitleUtil.hideEventTitleFromPlayer(playerRefComponent, 0.0F);
                                });
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }, 3L, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                // Ignore
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error spawning comet: " + e.getMessage());
        }
    }

    private int parseZoneId(String name) {
        if (name == null || name.isEmpty()) return 0;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)zone(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(name);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Fall through
            }
        }

        java.util.regex.Pattern fallbackPattern = java.util.regex.Pattern.compile("\\d+");
        java.util.regex.Matcher fallbackMatcher = fallbackPattern.matcher(name);

        if (fallbackMatcher.find()) {
            try {
                return Integer.parseInt(fallbackMatcher.group());
            } catch (NumberFormatException e) {
                // Fall through
            }
        }

        return 0;
    }

    private CometTier selectTierForZone(int zoneId) {
        Random random = new Random();
        CometConfig config = CometConfig.getInstance();
        ZoneSpawnChances chances = (config != null)
            ? config.getZoneSpawnChances(zoneId)
            : ZoneSpawnChances.getDefaultForZone(zoneId);

        int selectedTier = chances.selectTier(random);
        switch (selectedTier) {
            case 1: return CometTier.UNCOMMON;
            case 2: return CometTier.RARE;
            case 3: return CometTier.EPIC;
            case 4: return CometTier.LEGENDARY;
            default: return CometTier.UNCOMMON;
        }
    }

    private boolean isInWater(World targetWorld, int x, int y, int z) {
        try {
            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
            com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk =
                targetWorld.getChunkIfInMemory(chunkIndex);

            if (chunk == null) chunk = targetWorld.getChunk(chunkIndex);
            if (chunk == null) return false;

            return chunk.getFluidId(x, y, z) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int findGroundLevel(World targetWorld, int x, int z, int startY) {
        int searchStartY = 255;
        int minY = Math.max(0, startY - 150);
        
        for (int y = searchStartY; y >= minY; y--) {
            try {
                com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = 
                    targetWorld.getBlockType(x, y, z);
                
                if (blockType != null) {
                    int blockId = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
                        .getAssetMap().getIndex(blockType.getId());
                    
                    if (blockId != 0) {
                        Object material = blockType.getMaterial();
                        if (material != null) {
                            String materialStr = material.toString();
                            if (materialStr.equals("Solid") || materialStr.equals("Opaque")) {
                                return y;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
        
        return -1;
    }
}
