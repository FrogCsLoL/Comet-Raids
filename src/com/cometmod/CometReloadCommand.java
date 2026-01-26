package com.cometmod;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Command to reload the Comet Mod configuration from file.
 * Usage: /comet reload
 */
public class CometReloadCommand extends AbstractWorldCommand {

    private static final Logger LOGGER = Logger.getLogger("CometReloadCommand");

    public CometReloadCommand() {
        super("reload", "Reloads the Comet Mod configuration from file");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {

        try {
            context.sendMessage(Message.raw("Reloading Comet Mod configuration..."));

            // Reload config
            CometConfig config = CometConfig.reload();

            // Apply spawn settings to spawn task
            CometSpawnTask spawnTask = CometModPlugin.getSpawnTask();
            if (spawnTask != null) {
                config.applyToSpawnTask(spawnTask);
            }

            // Apply despawn time
            CometFallingSystem.setDespawnTimeMinutes(config.despawnTimeMinutes);

            // Reload fixed spawn points
            FixedSpawnManager fixedSpawnManager = CometModPlugin.getFixedSpawnManager();
            int fixedSpawnCount = 0;
            if (fixedSpawnManager != null) {
                fixedSpawnManager.reload();
                fixedSpawnCount = fixedSpawnManager.getSpawnPoints().size();
            }

            // Report results
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration reloaded!\n");
            sb.append("Spawn Settings:\n");
            sb.append("  - Delay: ").append(config.minDelaySeconds).append("-").append(config.maxDelaySeconds)
                    .append("s\n");
            sb.append("  - Chance: ").append((int) (config.spawnChance * 100)).append("%\n");
            sb.append("  - Distance: ").append(config.minSpawnDistance).append("-").append(config.maxSpawnDistance)
                    .append(" blocks\n");
            sb.append("  - Despawn: ").append(config.despawnTimeMinutes).append(" min\n");
            sb.append("Themes: ").append(config.getThemeCount()).append(" loaded\n");
            sb.append("Fixed Spawns: ").append(fixedSpawnCount).append(" configured");

            if (!config.hasThemes()) {
                sb.append("\nWARNING: No themes defined! Waves will not work!");
            }

            context.sendMessage(Message.raw(sb.toString()));
            LOGGER.info("Configuration reloaded via command");

        } catch (Exception e) {
            context.sendMessage(Message.raw("Error reloading config: " + e.getMessage()));
            LOGGER.severe("Error reloading config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
