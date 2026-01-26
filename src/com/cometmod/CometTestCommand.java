package com.cometmod;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Test command to simulate automatic comet spawning
 */
public class CometTestCommand extends AbstractWorldCommand {
    
    private static final Logger LOGGER = Logger.getLogger("CometTestCommand");
    
    public CometTestCommand() {
        super("test", "Test automatic comet spawning (simulates zone-based spawn)");
    }
    
    @Override
    protected void execute(@Nonnull CommandContext context, 
                          @Nonnull com.hypixel.hytale.server.core.universe.world.World world, 
                          @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return;
        }
        
        try {
            Player player = context.senderAs(Player.class);
            
            // Get player's zone
            WorldMapTracker tracker = player.getWorldMapTracker();
            WorldMapTracker.ZoneDiscoveryInfo zoneInfo = tracker != null ? tracker.getCurrentZone() : null;
            String zoneName = zoneInfo != null ? zoneInfo.zoneName() : "Unknown";
            
            // Get spawn task (world and store are already provided as parameters)
            CometSpawnTask spawnTask = CometModPlugin.getSpawnTask();
            if (spawnTask == null) {
                context.sendMessage(Message.raw("Error: Spawn task not initialized!"));
                return;
            }
            
            // Simulate spawn for this player
            context.sendMessage(Message.raw("Simulating comet spawn for zone: " + zoneName));
            LOGGER.info("Test command: Simulating spawn for player " + player.getDisplayName() + " in zone " + zoneName);
            
            // Trigger spawn immediately (spawnForPlayer handles world.execute internally)
            spawnTask.spawnForPlayer(player);
            
            context.sendMessage(Message.raw("Comet spawn triggered! Check the sky!"));
            
        } catch (Exception e) {
            LOGGER.warning("Error in test command: " + e.getMessage());
            e.printStackTrace();
            context.sendMessage(Message.raw("Error: " + e.getMessage()));
        }
    }
}
