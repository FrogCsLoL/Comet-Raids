package com.cometmod;

import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.util.logging.Logger;

public class CometModPlugin extends JavaPlugin {

    private static final Logger LOGGER = Logger.getLogger("CometMod");
    private final CometWaveManager waveManager = new CometWaveManager();
    private static CometModPlugin instance;
    private java.util.concurrent.ScheduledFuture<?> timeoutTask;
    private java.util.concurrent.ScheduledFuture<?> fallingCheckTask;
    private CometFallingSystem fallingSystem;
    private CometSpawnTask spawnTask;
    private CometConfig config;
    private FixedSpawnManager fixedSpawnManager;

    public CometModPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        waveManager.setPlugin(this);
    }

    public static CometModPlugin getInstance() {
        return instance;
    }

    public static CometWaveManager getWaveManager() {
        return instance != null ? instance.waveManager : null;
    }

    public static CometFallingSystem getFallingSystem() {
        return instance != null ? instance.fallingSystem : null;
    }

    public static void setFallingSystem(CometFallingSystem fallingSystem) {
        if (instance == null) return;
        instance.fallingSystem = fallingSystem;

        if (instance.spawnTask == null && fallingSystem != null) {
            com.hypixel.hytale.server.core.universe.world.World world = fallingSystem.getWorld();
            if (world != null) {
                try {
                    com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = world
                            .getEntityStore().getStore();
                    if (store != null) {
                        instance.spawnTask = new CometSpawnTask(world, store);
                        if (instance.config != null) {
                            instance.config.applyToSpawnTask(instance.spawnTask);
                        }
                        instance.spawnTask.start();

                        try {
                            double despawnMinutes = CometFallingSystem.getDespawnTimeMinutes();
                            CometDespawnTracker.getInstance().processOnStartup(world, despawnMinutes);
                        } catch (Exception ex) {
                            LOGGER.warning("Failed to process despawn tracker: " + ex.getMessage());
                        }

                        // Start fixed spawn manager
                        if (instance.fixedSpawnManager != null) {
                            instance.fixedSpawnManager.start(world, store);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to initialize CometSpawnTask: " + e.getMessage());
                }
            }
        }
    }

    public static CometSpawnTask getSpawnTask() {
        return instance != null ? instance.spawnTask : null;
    }

    public static FixedSpawnManager getFixedSpawnManager() {
        return instance != null ? instance.fixedSpawnManager : null;
    }

    public CometConfig getConfig() {
        return this.config;
    }

    @Override
    protected void setup() {
        LOGGER.info("CometMod setup...");

        getCodecRegistry(Interaction.CODEC).register("Comet_Stone_Uncommon_Activate",
                CometStoneActivateInteraction.class, CometStoneActivateInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("Comet_Stone_Epic_Activate",
                CometStoneActivateInteraction.class, CometStoneActivateInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("Comet_Stone_Rare_Activate",
                CometStoneActivateInteraction.class, CometStoneActivateInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("Comet_Stone_Legendary_Activate",
                CometStoneActivateInteraction.class, CometStoneActivateInteraction.CODEC);

        getEventRegistry().registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        com.hypixel.hytale.server.core.command.system.CommandManager.get().registerSystemCommand(new CometCommand());
        com.hypixel.hytale.server.core.command.system.CommandManager.get().registerSystemCommand(new CometConfigCommand());

        getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent.class,
                event -> {
                    try {
                        com.hypixel.hytale.server.core.entity.entities.Player player = event.getPlayer();
                        if (player == null) return;

                        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerRef = player.getReference();

                        if (playerRef != null && playerRef.isValid() && spawnTask == null) {
                            try {
                                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = playerRef.getStore();
                                if (store != null) {
                                    Object externalData = store.getExternalData();
                                    if (externalData instanceof com.hypixel.hytale.server.core.universe.world.storage.EntityStore) {
                                        com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) externalData).getWorld();
                                        if (world != null) {
                                            this.spawnTask = new CometSpawnTask(world, store);
                                            if (this.config != null) {
                                                this.config.applyToSpawnTask(this.spawnTask);
                                            }
                                            this.spawnTask.start();

                                            try {
                                                double despawnMinutes = CometFallingSystem.getDespawnTimeMinutes();
                                                CometDespawnTracker.getInstance().processOnStartup(world, despawnMinutes);
                                            } catch (Exception ex) {
                                                // Ignore
                                            }

                                            // Start fixed spawn manager
                                            if (this.fixedSpawnManager != null) {
                                                this.fixedSpawnManager.start(world, store);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }

                        if (spawnTask != null) {
                            spawnTask.addPlayer(player);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                });

        getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent.class,
                event -> {
                    try {
                        com.hypixel.hytale.server.core.universe.world.World world = event.getWorld();
                        world.getWorldMapManager().addMarkerProvider("comets", CometMarkerProvider.INSTANCE);
                    } catch (Exception e) {
                        // Ignore
                    }
                });

        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                CometWaveManager wm = getWaveManager();
                if (wm != null) wm.checkTimeouts();
            } catch (Exception e) {
                // Ignore
            }
        }, 5L, 5L, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    protected void start() {
        LOGGER.info("CometMod started!");

        try {
            for (com.hypixel.hytale.server.core.universe.world.World world : com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorlds().values()) {
                try {
                    world.getWorldMapManager().addMarkerProvider("comets", CometMarkerProvider.INSTANCE);
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            getEntityStoreRegistry().registerSystem(new CometDeathDetectionSystem(waveManager));
        } catch (Exception e) {
            LOGGER.warning("Failed to register CometDeathDetectionSystem: " + e.getMessage());
        }

        try {
            getEntityStoreRegistry().registerSystem(new CometBlockBreakSystem(waveManager));
        } catch (Exception e) {
            LOGGER.warning("Failed to register CometBlockBreakSystem: " + e.getMessage());
        }

        try {
            getEntityStoreRegistry().registerSystem(new CometStatModifierSystem());
        } catch (Exception e) {
            LOGGER.warning("Failed to register CometStatModifierSystem: " + e.getMessage());
        }

        try {
            getEntityStoreRegistry().registerSystem(new CometDamageModifierSystem());
        } catch (Exception e) {
            LOGGER.warning("Failed to register CometDamageModifierSystem: " + e.getMessage());
        }

        this.fallingSystem = null;

        CometConfig config = CometConfig.load();
        CometFallingSystem.setDespawnTimeMinutes(config.despawnTimeMinutes);

        this.spawnTask = null;
        this.config = config;

        // Initialize fixed spawn manager
        this.fixedSpawnManager = new FixedSpawnManager();
        this.fixedSpawnManager.load();
        this.fallingCheckTask = com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try {
                        if (this.fallingSystem != null) {
                            com.hypixel.hytale.server.core.universe.world.World world = this.fallingSystem.getWorld();
                            if (world != null) {
                                world.execute(() -> {
                                    try {
                                        com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = world
                                                .getEntityStore().getStore();
                                        if (store != null) {
                                            this.fallingSystem.checkProjectilesFallback(world, store);
                                        }
                                    } catch (Exception e) {
                                        // Ignore
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                },
                1000L, 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        this.timeoutTask = com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> {
                    try {
                        waveManager.checkTimeouts();
                    } catch (Exception e) {
                        // Ignore
                    }
                },
                5000L, 5000L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutdown() {
        LOGGER.info("CometMod shutdown");
        if (timeoutTask != null) timeoutTask.cancel(false);
        if (fallingCheckTask != null) fallingCheckTask.cancel(false);
        if (spawnTask != null) spawnTask.stop();
        if (fixedSpawnManager != null) fixedSpawnManager.stop();
        waveManager.cleanup();
    }

    private void onEntityRemove(EntityRemoveEvent event) {
        com.hypixel.hytale.server.core.entity.Entity entity = event.getEntity();
        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef = entity.getReference();

        if (this.fallingSystem != null && entityRef != null && entityRef.isValid()) {
            try {
                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = entityRef.getStore();
                if (store != null) {
                    com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent = store.getComponent(entityRef,
                            com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());

                    if (uuidComponent != null) {
                        java.util.UUID entityUUID = uuidComponent.getUuid();
                        com.hypixel.hytale.math.vector.Vector3i targetPos = this.fallingSystem.getTrackedTarget(entityUUID);

                        if (targetPos != null) {
                            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform = store
                                    .getComponent(entityRef,
                                            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());

                            com.hypixel.hytale.server.core.universe.world.World world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store
                                    .getExternalData()).getWorld();

                            if (world != null && transform != null) {
                                com.hypixel.hytale.math.vector.Vector3d landingPos = transform.getPosition();

                                world.execute(() -> {
                                    int blockX = (int) Math.round(landingPos.x);
                                    int blockZ = (int) Math.round(landingPos.z);
                                    int landingBlockY = (int) Math.floor(landingPos.y);
                                    int solidGroundY = this.fallingSystem.findGroundLevelAtPosition(world, blockX, blockZ, landingBlockY);
                                    int blockY = (solidGroundY != -1) ? solidGroundY + 1 : landingBlockY + 1;

                                    com.hypixel.hytale.math.vector.Vector3i actualBlockPos = new com.hypixel.hytale.math.vector.Vector3i(blockX, blockY, blockZ);
                                    CometTier tier = this.fallingSystem.getProjectileTier(entityUUID);
                                    String themeId = this.fallingSystem.getProjectileThemeId(entityUUID);
                                    java.util.UUID ownerUUID = this.fallingSystem.getProjectileOwner(entityUUID);
                                    this.fallingSystem.spawnCometBlock(world, actualBlockPos, store, tier, themeId, ownerUUID);
                                    this.fallingSystem.removeTrackedProjectile(entityUUID);
                                });
                            } else if (world != null) {
                                CometTier tier = this.fallingSystem.getProjectileTier(entityUUID);
                                String themeId = this.fallingSystem.getProjectileThemeId(entityUUID);
                                java.util.UUID ownerUUID = this.fallingSystem.getProjectileOwner(entityUUID);
                                world.execute(() -> {
                                    this.fallingSystem.spawnCometBlock(world, targetPos, store, tier, themeId, ownerUUID);
                                    this.fallingSystem.removeTrackedProjectile(entityUUID);
                                });
                            }
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        if (entityRef != null && entityRef.isValid()) {
            waveManager.handleMobDeath(entityRef);
        }
    }
}
