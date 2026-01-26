package com.cometmod;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.SystemGroup;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * System that modifies damage dealt by comet NPCs based on config multipliers.
 * 
 * Works by:
 * 1. Intercepting damage events before they are applied
 * 2. Checking if the damage source is a comet NPC with a damage multiplier
 * 3. Multiplying the damage amount by the configured multiplier
 */
public class CometDamageModifierSystem extends DamageEventSystem {

    private static final Logger LOGGER = Logger.getLogger("CometDamageModifier");

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, Damage damage) {
        try {
            // Get the damage source
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource)) {
                return; // Not entity damage, skip
            }

            Damage.EntitySource entitySource = (Damage.EntitySource) source;
            Ref<EntityStore> sourceRef = entitySource.getRef();

            if (sourceRef == null || !sourceRef.isValid()) {
                return;
            }

            // Check if source is a player (don't modify player damage)
            if (Player.getComponentType() != null) {
                Player player = store.getComponent(sourceRef, Player.getComponentType());
                if (player != null) {
                    return; // Source is a player, don't modify
                }
            }

            // Check if source is an NPC
            if (NPCEntity.getComponentType() == null) {
                return;
            }

            NPCEntity npc = store.getComponent(sourceRef, NPCEntity.getComponentType());
            if (npc == null) {
                return; // Source is not an NPC
            }

            // Get the NPC's UUID
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent = store.getComponent(sourceRef,
                    com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return;
            }

            UUID npcUUID = uuidComponent.getUuid();

            // Get damage multiplier from our tracking system
            float damageMultiplier = CometStatModifierSystem.getDamageMultiplier(npcUUID);

            if (damageMultiplier == 1.0f) {
                return; // No multiplier (1x), skip
            }

            // Apply the damage multiplier
            float originalDamage = damage.getAmount();
            float newDamage = originalDamage * damageMultiplier;

            // Ensure damage doesn't go negative
            newDamage = Math.max(0, newDamage);

            damage.setAmount(newDamage);

            LOGGER.info("[CometDamageModifier] Modified damage from NPC " + npc.getRoleName() +
                    ": " + originalDamage + " -> " + newDamage + " (" + damageMultiplier + "x)");

        } catch (Exception e) {
            LOGGER.warning("[CometDamageModifier] Error in handle: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
