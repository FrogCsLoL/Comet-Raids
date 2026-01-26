package com.cometmod;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.logging.Logger;

public class SpawnCustomNPCCommand extends AbstractWorldCommand {
    
    private static final Logger LOGGER = Logger.getLogger("SpawnCustomNPCCommand");
    private static final int SPAWN_DISTANCE = 10; // 10 blocks from player
    private static final Random RANDOM = new Random();
    private static final String CUSTOM_NPC_TYPE = "CustomNPC_MyCharacter";
    
    public SpawnCustomNPCCommand() {
        super("spawncustomnpc", "Spawns your custom NPC character near you");
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
        
        try {
            // Get player
            Player player = context.senderAs(Player.class);
            Ref<EntityStore> playerRef = player.getReference();
            
            if (playerRef == null || !playerRef.isValid()) {
                context.sendMessage(Message.raw("Error: Could not get player reference!"));
                return;
            }
            
            // Get player position from TransformComponent
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform = 
                store.getComponent(playerRef, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Error: Could not get player position!"));
                return;
            }
            Vector3d playerPos = transform.getPosition();
            LOGGER.info("Player position: " + playerPos);
            
            // Find random position 10 blocks away
            double angle = RANDOM.nextDouble() * 2 * Math.PI; // Random angle
            double distance = 8 + RANDOM.nextDouble() * 4; // 8-12 blocks
            
            double spawnX = playerPos.x + Math.cos(angle) * distance;
            double spawnZ = playerPos.z + Math.sin(angle) * distance;
            double spawnY = playerPos.y; // Spawn at player height
            
            Vector3d spawnPos = new Vector3d(spawnX, spawnY, spawnZ);
            com.hypixel.hytale.math.vector.Vector3f rotation = new com.hypixel.hytale.math.vector.Vector3f(0, (float)(angle * 180 / Math.PI), 0);
            
            // Get NPCPlugin
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                context.sendMessage(Message.raw("Error: NPCPlugin not available!"));
                LOGGER.warning("NPCPlugin not available!");
                return;
            }
            
            // Try spawning with the custom model name first
            // If that fails, try "Player" since our model extends Player
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> result = null;
            
            result = npcPlugin.spawnNPC(store, CUSTOM_NPC_TYPE, null, spawnPos, rotation);
            
            // If custom model fails, try Player model (our model extends Player)
            if (result == null || result.first() == null) {
                LOGGER.info("Custom model not found, trying Player model...");
                result = npcPlugin.spawnNPC(store, "Player", null, spawnPos, rotation);
            }
            
            if (result != null && result.first() != null) {
                context.sendMessage(Message.raw("Custom NPC spawned successfully!"));
                LOGGER.info("Spawned custom NPC at " + spawnPos);
            } else {
                context.sendMessage(Message.raw("Error: Failed to spawn NPC. Model: " + CUSTOM_NPC_TYPE));
                LOGGER.warning("Failed to spawn custom NPC: " + CUSTOM_NPC_TYPE);
                LOGGER.info("Make sure the model file exists at: Server/Models/" + CUSTOM_NPC_TYPE + ".json");
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error spawning custom NPC: " + e.getMessage());
            e.printStackTrace();
            context.sendMessage(Message.raw("Error: " + e.getMessage()));
        }
    }
}
