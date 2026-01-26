package com.cometmod;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * System that applies stat modifiers (HP, damage, scale) to NPCs spawned by
 * comets.
 * 
 * Works by:
 * 1. Tracking which NPCs belong to which comet wave (via UUID mapping)
 * 2. When NPC is added, check if it's a tracked comet NPC
 * 3. Apply HP multiplier via EntityStatMap modifier
 * 4. Apply scale multiplier via EntityScaleComponent
 * 5. Damage multiplier is handled separately in CometDamageModifierSystem
 */
public class CometStatModifierSystem extends HolderSystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger("CometStatModifier");

    // Key for the stat modifier we add to entities
    public static final String HP_MODIFIER_KEY = "comet_hp_multiplier";
    public static final String SPEED_MODIFIER_KEY = "comet_speed_multiplier";

    // Track NPC UUIDs that need stat modifications
    // Map: NPC UUID -> StatModifiers (hp, damage, scale, speed multipliers)
    private static final Map<UUID, StatModifiers> pendingModifiers = new ConcurrentHashMap<>();

    // Track which NPCs have already been modified (to prevent double-application)
    private static final Map<UUID, Boolean> modifiedNPCs = new ConcurrentHashMap<>();

    /**
     * Register an NPC UUID to receive stat modifiers when it spawns
     */
    public static void registerPendingModifier(UUID npcUUID, float hpMultiplier, float damageMultiplier,
            float scaleMultiplier, float speedMultiplier) {
        pendingModifiers.put(npcUUID,
                new StatModifiers(hpMultiplier, damageMultiplier, scaleMultiplier, speedMultiplier));
        LOGGER.info("[CometStatModifier] Registered pending modifier for UUID " + npcUUID +
                " - HP: " + hpMultiplier + "x, Damage: " + damageMultiplier + "x, Scale: " + scaleMultiplier
                + "x, Speed: "
                + speedMultiplier + "x");
    }

    /**
     * Get pending damage multiplier for an NPC (used by damage system)
     */
    public static float getDamageMultiplier(UUID npcUUID) {
        StatModifiers mods = pendingModifiers.get(npcUUID);
        if (mods != null) {
            return mods.damageMultiplier;
        }
        return 1.0f;
    }

    /**
     * Check if an NPC has been modified
     */
    public static boolean isModified(UUID npcUUID) {
        return modifiedNPCs.containsKey(npcUUID);
    }

    /**
     * Clear tracking for an NPC (when it dies/despawns)
     */
    public static void clearNPC(UUID npcUUID) {
        pendingModifiers.remove(npcUUID);
        modifiedNPCs.remove(npcUUID);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
        try {
            // Skip if not an NPC
            if (NPCEntity.getComponentType() == null)
                return;
            NPCEntity npc = holder.getComponent(NPCEntity.getComponentType());
            if (npc == null)
                return;

            // Skip players
            if (Player.getComponentType() != null && holder.getComponent(Player.getComponentType()) != null) {
                return;
            }

            // Get the NPC's UUID
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent = holder
                    .getComponent(com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent == null)
                return;

            UUID npcUUID = uuidComponent.getUuid();

            // Check if this NPC has pending modifiers
            StatModifiers mods = pendingModifiers.get(npcUUID);
            if (mods == null || modifiedNPCs.containsKey(npcUUID)) {
                return;
            }

            // Apply modifiers
            applyModifiers(holder, mods.hpMultiplier, mods.damageMultiplier, mods.scaleMultiplier,
                    mods.speedMultiplier);

            // Mark as modified and clean up pending
            modifiedNPCs.put(npcUUID, true);

        } catch (Exception e) {
            LOGGER.warning("[CometStatModifier] Error in onEntityAdd: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Directly apply modifiers to an entity holder.
     */
    public void applyModifiers(Holder<EntityStore> holder, float hpMult, float damageMult, float scaleMult,
            float speedMult) {
        try {
            LOGGER.info("[CometStatModifier] Applying modifiers to entity - HP: " + hpMult + "x, Scale: " + scaleMult
                    + "x, Speed: " + speedMult + "x");

            // Apply HP multiplier
            if (hpMult != 1.0f) {
                EntityStatMap statMap = holder.getComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    applyHealthToMap(statMap, hpMult);
                }
            }

            // Apply Speed multiplier
            if (speedMult != 1.0f) {
                EntityStatMap statMap = holder.getComponent(EntityStatMap.getComponentType());
                if (statMap != null) {
                    applySpeedToMap(statMap, speedMult);
                }
            }

            // Apply scale multiplier
            if (scaleMult != 1.0f) {
                EntityScaleComponent scaleComp = holder.getComponent(EntityScaleComponent.getComponentType());
                if (scaleComp != null) {
                    scaleComp.setScale(scaleComp.getScale() * scaleMult);
                } else {
                    holder.putComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scaleMult));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[CometStatModifier] Error applying modifiers (holder): " + e.getMessage());
        }
    }

    /**
     * Directly apply modifiers to an entity via store and ref.
     */
    public static void applyModifiers(Store<EntityStore> store, Ref<EntityStore> ref, float hpMult, float damageMult,
            float scaleMult, float speedMult) {
        try {
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComp = store.getComponent(ref,
                    com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            UUID npcUUID = uuidComp != null ? uuidComp.getUuid() : null;

            if (npcUUID != null && modifiedNPCs.containsKey(npcUUID)) {
                return;
            }

            if (npcUUID != null) {
                LOGGER.info("[CometStatModifier] Applying modifiers to UUID " + npcUUID + " - HP: " + hpMult
                        + "x, Scale: " + scaleMult + "x, Speed: " + speedMult + "x");
                pendingModifiers.put(npcUUID, new StatModifiers(hpMult, damageMult, scaleMult, speedMult));
                modifiedNPCs.put(npcUUID, true);
            }

            // Apply HP multiplier
            if (hpMult != 1.0f) {
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    applyHealthToMap(statMap, hpMult);
                }
            }

            // Apply Speed multiplier
            if (speedMult != 1.0f) {
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    applySpeedToMap(statMap, speedMult);
                }
            }

            // Apply scale multiplier
            if (scaleMult != 1.0f) {
                EntityScaleComponent scaleComp = store.getComponent(ref, EntityScaleComponent.getComponentType());
                if (scaleComp != null) {
                    scaleComp.setScale(scaleComp.getScale() * scaleMult);
                } else {
                    store.putComponent(ref, EntityScaleComponent.getComponentType(),
                            new EntityScaleComponent(scaleMult));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[CometStatModifier] Error applying modifiers (ref): " + e.getMessage());
        }
    }

    private static void applyHealthToMap(EntityStatMap statMap, float multiplier) {
        try {
            int statIndex = EntityStatType.getAssetMap().getIndex("Health");
            if (statIndex < 0)
                return;

            // Get the entity's CURRENT max health (not the asset default)
            float currentMax = statMap.get(statIndex).getMax();

            LOGGER.info("[CometStatModifier] Applying HP modifier: currentMax=" + currentMax + ", multiplier=" + multiplier);

            // Use MULTIPLICATIVE modifier to properly scale HP
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.MULTIPLICATIVE,
                    multiplier);

            statMap.putModifier(EntityStatMap.Predictable.ALL, statIndex, HP_MODIFIER_KEY, (Modifier) modifier);

            // Log the result after applying modifier
            float newMax = statMap.get(statIndex).getMax();
            float newCurrent = statMap.get(statIndex).get();
            LOGGER.info("[CometStatModifier] After putModifier - newMax=" + newMax + ", newCurrent=" + newCurrent);

            statMap.maximizeStatValue(EntityStatMap.Predictable.ALL, statIndex);

            float finalCurrent = statMap.get(statIndex).get();
            LOGGER.info("[CometStatModifier] After maximizeStatValue - finalCurrent=" + finalCurrent);
        } catch (Exception e) {
            LOGGER.warning("[CometStatModifier] Error applying health to map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void applySpeedToMap(EntityStatMap statMap, float multiplier) {
        try {
            int statIndex = EntityStatType.getAssetMap().getIndex("Speed");
            if (statIndex < 0) {
                LOGGER.warning("[CometStatModifier] Speed stat index not found!");
                return;
            }

            // Get the entity's CURRENT max speed (not the asset default)
            float currentMax = statMap.get(statIndex).getMax();
            float currentValue = statMap.get(statIndex).get();

            LOGGER.info("[CometStatModifier] Applying Speed modifier: currentMax=" + currentMax + ", currentValue=" + currentValue + ", multiplier=" + multiplier);

            // Use MULTIPLICATIVE modifier to properly scale Speed
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.MULTIPLICATIVE,
                    multiplier);

            statMap.putModifier(EntityStatMap.Predictable.ALL, statIndex, SPEED_MODIFIER_KEY, (Modifier) modifier);

            // Log the result after applying modifier
            float newMax = statMap.get(statIndex).getMax();
            float newCurrent = statMap.get(statIndex).get();
            LOGGER.info("[CometStatModifier] After putModifier - newMax=" + newMax + ", newCurrent=" + newCurrent);

            // Maximize the speed value to match the new max
            statMap.maximizeStatValue(EntityStatMap.Predictable.ALL, statIndex);

            float finalCurrent = statMap.get(statIndex).get();
            LOGGER.info("[CometStatModifier] After maximizeStatValue - finalCurrent=" + finalCurrent);
        } catch (Exception e) {
            LOGGER.warning("[CometStatModifier] Error applying speed to map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onEntityRemoved(Holder<EntityStore> holder, RemoveReason reason, Store<EntityStore> store) {
        // Clean up tracking when NPC is removed
        try {
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComponent = holder
                    .getComponent(com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent != null) {
                clearNPC(uuidComponent.getUuid());
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Holds the stat multipliers for an NPC
     */
    public static class StatModifiers {
        public final float hpMultiplier;
        public final float damageMultiplier;
        public final float scaleMultiplier;
        public final float speedMultiplier;

        public StatModifiers(float hp, float damage, float scale, float speed) {
            this.hpMultiplier = hp;
            this.damageMultiplier = damage;
            this.scaleMultiplier = scale;
            this.speedMultiplier = speed;
        }
    }
}
