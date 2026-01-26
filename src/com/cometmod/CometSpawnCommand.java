package com.cometmod;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.logging.Logger;

public class CometSpawnCommand extends AbstractWorldCommand {

    private static final Logger LOGGER = Logger.getLogger("CometSpawnCommand");
    private static final Random RANDOM = new Random();

    // Optional tier argument
    private final OptionalArg<String> tierArg;
    // Optional theme argument
    private final OptionalArg<String> themeArg;
    // Optional flag to spawn comet directly above player
    private final OptionalArg<String> onMeArg;

    public CometSpawnCommand() {
        super("spawn", "Spawns a comet near the player");
        this.tierArg = withOptionalArg("tier", "Tier of the comet (Uncommon, Epic, Rare, Legendary)", ArgTypes.STRING);
        this.themeArg = withOptionalArg("theme", "Theme of the wave (e.g. Skeleton, Goblin, Spider)", ArgTypes.STRING);
        this.onMeArg = withOptionalArg("onme", "Use --onme true to spawn comet directly above the player", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {

        // Check if sender is a player
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return;
        }

        // Get tier from argument (default to Uncommon)
        CometTier tier = CometTier.UNCOMMON;

        if (tierArg.provided(context)) {
            String tierString = tierArg.get(context);
            if (tierString != null && !tierString.isEmpty()) {
                tier = CometTier.fromString(tierString);

                // Validate: if tier is still UNCOMMON but the arg wasn't "uncommon", it's
                // invalid
                if (tier == CometTier.UNCOMMON && !tierString.equalsIgnoreCase("uncommon")) {
                    context.sendMessage(Message.raw("Invalid tier! Valid tiers: Uncommon, Epic, Rare, Legendary"));
                    return;
                }
            }
        }

        // Get theme from argument (optional) - now uses string-based theme IDs
        String themeId = null;
        String themeNameStr = null;

        if (themeArg.provided(context)) {
            String themeArgStr = themeArg.get(context);
            if (themeArgStr != null && !themeArgStr.isEmpty()) {
                // Replace underscores with spaces to support multi-word themes (e.g.
                // "Legendary_Earth")
                // consistent with command line usage where spaces split arguments
                themeArgStr = themeArgStr.replace('_', ' ');

                CometWaveManager waveManager = CometModPlugin.getWaveManager();
                if (waveManager != null) {
                    themeId = waveManager.getThemeIdByName(themeArgStr);
                    if (themeId == null) {
                        context.sendMessage(Message.raw("Invalid theme! Valid themes: " +
                                String.join(", ", waveManager.getThemeNames())));
                        return;
                    }
                    themeNameStr = WaveThemeProvider.getThemeName(themeId);
                }
            }
        }

        try {
            // Get player
            Player player = context.senderAs(Player.class);
            Ref<EntityStore> playerRef = player.getReference();

            if (playerRef == null || !playerRef.isValid()) {
                context.sendMessage(Message.raw("Error: Could not get player reference!"));
                return;
            }

            // Get player position from TransformComponent
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform = store.getComponent(
                    playerRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Error: Could not get player position!"));
                return;
            }
            Vector3d playerPos = transform.getPosition();

            // Check if --onme flag is provided (spawn directly above player)
            boolean spawnOnPlayer = onMeArg.provided(context);

            int spawnX = 0, spawnY = -1, spawnZ = 0;
            boolean foundValidLocation = false;

            if (spawnOnPlayer) {
                // Spawn directly above the player's current position
                spawnX = (int) playerPos.x;
                spawnZ = (int) playerPos.z;
                spawnY = (int) playerPos.y;
                foundValidLocation = true;
                LOGGER.info("Spawning comet directly above player at X=" + spawnX + ", Z=" + spawnZ);
            } else {
                // Override for manual spawn command to keep it close (5-8 blocks)
                int minDist = 5;
                int maxDist = 8;

                // Try up to 16 times to find a valid position (ground, not water)
                for (int attempt = 0; attempt < 16; attempt++) {
                    double angle = RANDOM.nextDouble() * 2 * Math.PI;
                    double distance = minDist + RANDOM.nextDouble() * (maxDist - minDist);
                    int x = (int) (playerPos.x + Math.cos(angle) * distance);
                    int z = (int) (playerPos.z + Math.sin(angle) * distance);
                    int y = findGroundLevel(world, x, z, (int) playerPos.y);
                    if (y == -1)
                        continue;
                    if (isInWater(world, x, y, z) || isInWater(world, x, y + 1, z))
                        continue;
                    spawnX = x;
                    spawnY = y;
                    spawnZ = z;
                    foundValidLocation = true;
                    break;
                }
            }

            if (!foundValidLocation) {
                context.sendMessage(
                        Message.raw("Error: Could not find valid spawn location (ground or water) after 16 attempts!"));
                return;
            }

            // Target block position (1 block above ground)
            final Vector3i targetBlockPos = new Vector3i(spawnX, spawnY + 1, spawnZ);

            if (themeId != null) {
                CometWaveManager waveManager = CometModPlugin.getWaveManager();
                if (waveManager != null) {
                    waveManager.forceTheme(targetBlockPos, themeId);
                }
            }

            // Get projectile config (tier-specific)
            String projectileConfigName = tier.getFallingProjectileConfig();
            com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig projectileConfig = (com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig) com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig
                    .getAssetMap()
                    .getAsset(projectileConfigName);

            if (projectileConfig == null) {
                context.sendMessage(Message.raw("Error: " + projectileConfigName + " projectile config not found!"));
                return;
            }

            // Spawn position: 100 blocks above target (higher for better visibility)
            Vector3d spawnPos = new Vector3d(
                    targetBlockPos.x + 0.5,
                    targetBlockPos.y + 100.0,
                    targetBlockPos.z + 0.5);

            // Direction: straight down
            Vector3d direction = new Vector3d(0, -1, 0);

            // Initialize falling system if not already initialized
            CometFallingSystem fallingSystem = CometModPlugin.getFallingSystem();
            if (fallingSystem == null) {
                fallingSystem = new CometFallingSystem(world);
                CometModPlugin.setFallingSystem(fallingSystem);
            }

            // Store tier and owner UUID with the projectile tracking
            // We'll pass them to spawnCometBlock when projectile lands
            final CometTier finalTier = tier;
            final java.util.UUID ownerUUID = player.getUuid();

            // Generate UUID for tracking this projectile
            java.util.UUID projectileUUID = java.util.UUID.randomUUID();

            com.hypixel.hytale.component.CommandBuffer<EntityStore> commandBuffer = null;
            try {
                java.lang.reflect.Method takeCommandBufferMethod = store.getClass()
                        .getDeclaredMethod("takeCommandBuffer");
                takeCommandBufferMethod.setAccessible(true);
                commandBuffer = (com.hypixel.hytale.component.CommandBuffer<EntityStore>) takeCommandBufferMethod
                        .invoke(store);
            } catch (Exception e) {
                LOGGER.warning("Could not get command buffer: " + e.getMessage());
                spawnCometBlockDirectly(world, targetBlockPos, store, tier, themeId, ownerUUID);
                return;
            }

            Object externalData = commandBuffer.getExternalData();
            if (!(externalData instanceof com.hypixel.hytale.server.core.universe.world.storage.EntityStore)) {
                spawnCometBlockDirectly(world, targetBlockPos, store, tier, themeId, ownerUUID);
                return;
            }

            try {
                com.hypixel.hytale.component.Ref<EntityStore> projectileRef = com.hypixel.hytale.server.core.modules.projectile.ProjectileModule
                        .get()
                        .spawnProjectile(projectileUUID, playerRef, commandBuffer, projectileConfig, spawnPos,
                                direction);

                if (projectileRef != null) {
                    fallingSystem.trackProjectile(projectileUUID, targetBlockPos, spawnPos.y, finalTier, themeId,
                            ownerUUID);
                } else {
                    spawnCometBlockDirectly(world, targetBlockPos, store, finalTier, themeId, ownerUUID);
                }
            } catch (Exception e) {
                LOGGER.severe("Error spawning projectile: " + e.getMessage());
                spawnCometBlockDirectly(world, targetBlockPos, store, finalTier, themeId, ownerUUID);
            } finally {
                if (commandBuffer != null) {
                    try {
                        java.lang.reflect.Method consumeMethod = commandBuffer.getClass().getDeclaredMethod("consume");
                        consumeMethod.setAccessible(true);
                        consumeMethod.invoke(commandBuffer);
                    } catch (Exception e) {
                        LOGGER.warning("Could not consume command buffer: " + e.getMessage());
                    }
                }
            }

            // Send chat message
            context.sendMessage(Message.raw("Comet falling! Target: X=" + targetBlockPos.x +
                    ", Y=" + targetBlockPos.y +
                    ", Z=" + targetBlockPos.z +
                    (spawnOnPlayer ? " (DIRECTLY ABOVE YOU!)" : "") +
                    (themeId != null ? " (Theme: " + themeNameStr + ")" : "")));

            // Show title message
            PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent != null) {
                Message primaryTitle = Message.raw(tier.getName() + " Comet Falling!");
                Message secondaryTitle = Message.raw("Watch the sky!");

                EventTitleUtil.showEventTitleToPlayer(
                        playerRefComponent,
                        primaryTitle,
                        secondaryTitle,
                        true, // isMajor
                        null,
                        3.0F, // Show for 3 seconds
                        0.1F, // Fade in
                        0.5F // Fade out
                );

                // Auto-hide after 3 seconds
                com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    world.execute(() -> {
                        EventTitleUtil.hideEventTitleFromPlayer(playerRefComponent, 0.0F);
                    });
                }, 3L, java.util.concurrent.TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            LOGGER.severe("Error in comet spawn command: " + e.getMessage());
            e.printStackTrace();
            context.sendMessage(Message.raw("Error: " + e.getMessage()));
        }
    }

    private boolean isInWater(World world, int x, int y, int z) {
        try {
            long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
            com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);

            if (chunk == null) {
                chunk = world.getChunk(chunkIndex);
            }

            if (chunk == null) {
                return false;
            }

            return chunk.getFluidId(x, y, z) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int findGroundLevel(World world, int x, int z, int startY) {
        int searchStartY = 255;
        int minY = Math.max(0, startY - 150);

        long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
        com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);

        for (int y = searchStartY; y >= minY; y--) {
            try {
                com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = null;

                if (chunk != null) {
                    blockType = chunk.getBlockType(x, y, z);
                } else {
                    blockType = world.getBlockType(x, y, z);
                }

                if (blockType != null) {
                    int blockId = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap()
                            .getIndex(blockType.getId());

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

    private void spawnCometBlockDirectly(World world, Vector3i blockPos, Store<EntityStore> store, CometTier tier,
            String themeId, java.util.UUID ownerUUID) {
        world.execute(() -> {
            try {
                long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(
                        blockPos.x, blockPos.z);
                WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);

                if (chunk == null) {
                    chunk = world.getChunk(chunkIndex);
                }

                if (chunk == null) {
                    LOGGER.warning("Could not get chunk for comet at " + blockPos);
                    return;
                }

                int localX = blockPos.x & 31;
                int localZ = blockPos.z & 31;

                String blockIdName = tier.getBlockId("Comet_Stone");
                com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
                        .getAssetMap()
                        .getAsset(blockIdName);

                if (blockType == null) {
                    LOGGER.warning(blockIdName + " block type not found!");
                    return;
                }

                int blockId = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
                        .getAssetMap().getIndex(blockIdName);

                if (blockId == -1) {
                    LOGGER.warning(blockIdName + " block ID not found!");
                    return;
                }

                chunk.setBlock(localX, blockPos.y, localZ, blockId, blockType, 0, 0, 0);
                chunk.markNeedsSaving();

                CometWaveManager waveManager = CometModPlugin.getWaveManager();
                if (waveManager != null) {
                    waveManager.registerCometTier(world, blockPos, tier, ownerUUID);
                    if (themeId != null) {
                        waveManager.forceTheme(blockPos, themeId);
                    }
                }

                if (store != null) {
                    try {
                        Vector3d explosionPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
                        String explosionSystem = tier.getExplosionParticleSystem();
                        com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect(
                                explosionSystem,
                                explosionPos,
                                store);
                    } catch (Exception e) {
                        LOGGER.warning("Failed to spawn explosion particles: " + e.getMessage());
                    }
                }

                CometDespawnTracker.getInstance().registerComet(blockPos, tier.getName());
            } catch (Exception e) {
                LOGGER.severe("Error spawning comet block: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
