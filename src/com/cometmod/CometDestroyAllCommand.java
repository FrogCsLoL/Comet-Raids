package com.cometmod;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Command to destroy all comet blocks in the world.
 * Usage: /comet destroyall
 */
public class CometDestroyAllCommand extends AbstractWorldCommand {
    
    private static final Logger LOGGER = Logger.getLogger("CometDestroyAllCommand");
    
    public CometDestroyAllCommand() {
        super("destroyall", "Destroy all comet blocks in the world");
    }
    
    @Override
    protected void execute(@Nonnull CommandContext context, 
                          @Nonnull World world, 
                          @Nonnull Store<EntityStore> store) {
        
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return;
        }
        
        try {
            Player player = context.senderAs(Player.class);
            
            // Get all tracked comets from CometDespawnTracker
            CometDespawnTracker tracker = CometDespawnTracker.getInstance();
            List<Vector3i> cometPositions = new ArrayList<>();
            
            // Get all registered comet positions
            // We need to access the internal map - let's use a helper method
            java.util.Map<String, Long> spawnTimes = getCometSpawnTimes(tracker);
            
            if (spawnTimes != null && !spawnTimes.isEmpty()) {
                // Convert string keys to Vector3i positions
                for (String key : spawnTimes.keySet()) {
                    String[] parts = key.split(",");
                    if (parts.length == 3) {
                        try {
                            Vector3i pos = new Vector3i(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2])
                            );
                            cometPositions.add(pos);
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid position key: " + key);
                        }
                    }
                }
                LOGGER.info("Found " + cometPositions.size() + " tracked comets");
            }
            
            if (cometPositions.isEmpty()) {
                context.sendMessage(Message.raw("No tracked comet blocks found in the world."));
                return;
            }
            
            final int totalComets = cometPositions.size();
            context.sendMessage(Message.raw("Found " + totalComets + " comet block(s). Destroying..."));
            
            // Destroy all comets on the world thread
            world.execute(() -> {
                int destroyed = 0;
                CometWaveManager waveManager = CometModPlugin.getWaveManager();
                
                for (Vector3i pos : cometPositions) {
                    try {
                        // Check if block is actually a comet block before destroying
                        if (isCometBlock(world, pos)) {
                            // Clear any active waves for this comet
                            if (waveManager != null) {
                                // Remove from active comets map
                                java.util.Map<Vector3i, ?> activeComets = getActiveComets(waveManager);
                                if (activeComets != null) {
                                    activeComets.remove(pos);
                                }
                                // Remove from comet tiers map
                                java.util.Map<Vector3i, ?> cometTiers = getCometTiers(waveManager);
                                if (cometTiers != null) {
                                    cometTiers.remove(pos);
                                }
                            }
                            
                            // Remove the block
                            destroyCometBlock(world, pos);
                            
                            // Unregister from tracker
                            tracker.unregisterComet(pos);
                            
                            destroyed++;
                        } else {
                            // Block is not a comet, just unregister from tracker
                            tracker.unregisterComet(pos);
                            LOGGER.info("Position " + pos + " was tracked but block is not a comet, unregistered");
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Error destroying comet at " + pos + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                final int finalDestroyed = destroyed;
                // Send completion message on main thread
                com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
                    context.sendMessage(Message.raw("Destroyed " + finalDestroyed + " comet block(s)."));
                    LOGGER.info("Destroyed " + finalDestroyed + " comet blocks via /comet destroyall");
                });
            });
            
        } catch (Exception e) {
            LOGGER.severe("Error in destroyall command: " + e.getMessage());
            e.printStackTrace();
            context.sendMessage(Message.raw("Error: " + e.getMessage()));
        }
    }
    
    /**
     * Get comet spawn times map from tracker using reflection
     */
    private java.util.Map<String, Long> getCometSpawnTimes(CometDespawnTracker tracker) {
        try {
            java.lang.reflect.Field field = CometDespawnTracker.class.getDeclaredField("cometSpawnTimes");
            field.setAccessible(true);
            return (java.util.Map<String, Long>) field.get(tracker);
        } catch (Exception e) {
            LOGGER.warning("Could not access cometSpawnTimes via reflection: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get active comets map from wave manager using reflection
     */
    private java.util.Map<Vector3i, ?> getActiveComets(CometWaveManager waveManager) {
        try {
            java.lang.reflect.Field field = CometWaveManager.class.getDeclaredField("activeComets");
            field.setAccessible(true);
            return (java.util.Map<Vector3i, ?>) field.get(waveManager);
        } catch (Exception e) {
            LOGGER.warning("Could not access activeComets via reflection: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get comet tiers map from wave manager using reflection
     */
    private java.util.Map<Vector3i, ?> getCometTiers(CometWaveManager waveManager) {
        try {
            java.lang.reflect.Field field = CometWaveManager.class.getDeclaredField("cometTiers");
            field.setAccessible(true);
            return (java.util.Map<Vector3i, ?>) field.get(waveManager);
        } catch (Exception e) {
            LOGGER.warning("Could not access cometTiers via reflection: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a block at the given position is a comet block
     */
    private boolean isCometBlock(World world, Vector3i pos) {
        try {
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = 
                world.getBlockType(pos.x, pos.y, pos.z);
            
            if (blockType != null) {
                String blockId = blockType.getId();
                return blockId != null && blockId.startsWith("Comet_Stone_");
            }
        } catch (Exception e) {
            LOGGER.warning("Error checking block at " + pos + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Destroy a comet block at the given position
     */
    private void destroyCometBlock(World world, Vector3i pos) {
        try {
            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            
            if (chunk == null) {
                chunk = world.getChunk(chunkIndex);
            }
            
            if (chunk == null) {
                LOGGER.warning("Could not get chunk for comet block at " + pos);
                return;
            }
            
            // Get local chunk coordinates
            int localX = pos.x & 31;
            int localZ = pos.z & 31;
            
            // Set block to air (block ID 0)
            chunk.setBlock(localX, pos.y, localZ, 0, null, 0, 0, 0);
            chunk.markNeedsSaving();
            
            LOGGER.info("Destroyed comet block at " + pos);
        } catch (Exception e) {
            LOGGER.warning("Error destroying comet block at " + pos + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
