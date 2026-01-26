package com.cometmod;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class CometDeathDetectionSystem extends DamageEventSystem {
    private final CometWaveManager waveManager;

    public CometDeathDetectionSystem(CometWaveManager waveManager) {
        this.waveManager = waveManager;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                      @Nonnull Store<EntityStore> store,
                      @Nonnull CommandBuffer<EntityStore> commandBuffer,
                      @Nonnull Damage damage) {
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) return;

        commandBuffer.run(deferredStore -> {
            DeathComponent deathComponent = deferredStore.getComponent(entityRef, DeathComponent.getComponentType());
            if (deathComponent != null) {
                waveManager.handleMobDeath(entityRef);
            }
        });
    }
}
