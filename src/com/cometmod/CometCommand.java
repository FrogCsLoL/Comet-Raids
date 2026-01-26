package com.cometmod;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

public class CometCommand extends AbstractCommandCollection {

    public CometCommand() {
        super("comet", "Comet mod commands");
        addSubCommand(new CometSpawnCommand());
        addSubCommand(new CometTestCommand());
        addSubCommand(new CometZoneCommand());
        addSubCommand(new SpawnCustomNPCCommand());
        addSubCommand(new CometDestroyAllCommand());
        addSubCommand(new CometReloadCommand());
        addSubCommand(new CometSetSpawnCommand());
        addSubCommand(new CometScheduleSpawnCommand());
        addSubCommand(new CometRemoveSpawnCommand());
        addSubCommand(new CometListSpawnsCommand());
    }
}
