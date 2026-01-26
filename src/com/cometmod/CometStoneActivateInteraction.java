package com.cometmod;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

public class CometStoneActivateInteraction extends SimpleInstantInteraction {
    private static final Logger LOGGER = Logger.getLogger("CometStoneActivateInteraction");
    
    @Nonnull
    public static final BuilderCodec<CometStoneActivateInteraction> CODEC;

    static {
        CODEC = BuilderCodec
                .builder(CometStoneActivateInteraction.class, CometStoneActivateInteraction::new,
                        SimpleInstantInteraction.CODEC)
                .build();
    }

    @Override
    public boolean needsRemoteSync() {
        return false;
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null || playerRef == null || !playerRef.isValid()) return;

        com.hypixel.hytale.protocol.BlockPosition blockPosProtocol = context.getTargetBlock();
        if (blockPosProtocol == null) return;

        Vector3i blockPos = new Vector3i(blockPosProtocol.x, blockPosProtocol.y, blockPosProtocol.z);

        CometWaveManager waveManager = CometModPlugin.getWaveManager();
        if (waveManager == null) return;

        commandBuffer.run(store -> {
            try {
                CometWaveManager.CometState state = waveManager.getCometState(blockPos);
                if (state != CometWaveManager.CometState.COMPLETED) {
                    waveManager.handleCometActivation(store, playerRef, blockPos);
                }
            } catch (Exception e) {
                LOGGER.severe("Error activating comet: " + e.getMessage());
            }
        });
    }
}
