# Comet Raids

Ever wanted random events to spice up your Hytale gameplay? This mod adds falling comets that crash into your world, bringing waves of enemies to fight. Break the comet stone to start the encounter - survive all waves and claim your rewards.

This mod is built for players who want a raid-like experience and server owners who want a customizable reward system. You can create your own custom themes, define multi-wave encounters, override loot tables per theme, and tweak every aspect of the spawning and combat. Check the bottom of `comet_config.json` for examples of custom wave configurations.

> **Note:** This mod has only been tested in singleplayer. Multiplayer functionality should work but hasn't been thoroughly tested. If you run into bugs, please report them!

## Features

- **4 Comet Tiers** - Uncommon, Rare, Epic, and Legendary. Higher tiers = tougher fights, better loot.
- **Themed Waves** - Skeletons, goblins, spiders, trorks, outlanders, undead hordes... each comet picks a random theme (or you can force one).
- **Multi-Wave Combat** - Enemies spawn in waves. Clear one, the next begins. Rewards drop after the final wave.
- **Map Markers** - Comets show up on your map so you can track them down.
- **Fully Configurable** - Spawn rates, enemy counts, loot tables, despawn timers... tweak it all.

## Comet Ownership

By default, each comet is "owned" by the player it spawned for. Only that player can see the map marker and trigger the encounter by breaking the comet block. Other players can't interact with it.

If you want **any player** to be able to trigger **any comet** (useful for multiplayer servers), set `"globalComets": true` in the config. When enabled:
- All players see all comet markers on the map
- Any player can break and trigger any comet

## When Do Comets Spawn?

Comets spawn naturally based on these default settings:
- **Spawn interval**: Every 2-5 minutes (120-300 seconds)
- **Spawn chance**: 40% chance each time the interval triggers
- **Spawn distance**: 30-50 blocks away from a player
- **Despawn**: Unclaimed uncommon comets despawn after 30 minutes

The tier of comet that spawns depends on the zone you're in:
- **Zone 1**: 80% Uncommon, 20% Rare
- **Zone 2**: 40% Uncommon, 40% Rare, 20% Epic
- **Zone 3**: 30% Rare, 50% Epic, 20% Legendary
- **Zone 4**: 40% Epic, 60% Legendary

## Commands

| Command | Description |
|---------|-------------|
| `/comet spawn` | Spawns an Uncommon comet near you |
| `/comet spawn --tier Rare` | Spawns a specific tier (Uncommon, Rare, Epic, Legendary) |
| `/comet spawn --theme Skeleton` | Spawns with a specific theme |
| `/comet spawn --tier Legendary --theme Void` | Combine tier and theme |
| `/comet spawn --onme true` | Spawns comet directly above you (for testing) |
| `/comet reload` | Reloads the config from file |
| `/comet test` | Testing utilities |
| `/comet zone` | Zone management |
| `/comet destroyall` | Removes all active comets |

### Spawn Command Examples

```
/comet spawn
/comet spawn --tier Legendary
/comet spawn --tier Epic --theme Trork
/comet spawn --theme Undead
/comet spawn --tier Rare --theme Spider
/comet spawn --onme true --tier Legendary
```

### Available Themes

- `Skeleton` - Skeleton Horde (Tier 1-2)
- `Goblin` - Goblin Gang (Tier 1-2)
- `Spider` - Spider Swarm (Tier 1-2)
- `Trork` - Trork Warband (Tier 1-3)
- `Skeleton_Sand` - Sand Skeleton Legion (Tier 1-3)
- `Sabertooth` - Sabertooth Pack (Tier 1-3)
- `Void` - Voidspawn (Tier 1-3)
- `Outlander` - Outlander Cult (Tier 2-4)
- `Leopard` - Snow Leopard Pride (Tier 2-4)
- `Skeleton_Burnt` - Burnt Legion (Tier 3-4)
- `Ice` - Legendary Ice (Tier 3-4)
- `Burnt_Legendary` - Legendary Burnt (Tier 3-4)
- `Lava` - Legendary Lava (Tier 3-4)
- `Earth` - Legendary Earth (Tier 3-4)
- `Undead` - Undead Horde (Tier 1-4)
- `Zombie` - Zombie Aberration (Tier 3-4)

## Configuration

All settings live in `comet_config.json`. Open it with any text editor.

### Main Sections

**spawnSettings** - Controls natural comet spawning
```json
"spawnSettings": {
  "minDelaySeconds": 120,      // Minimum time between spawn attempts
  "maxDelaySeconds": 300,      // Maximum time between spawn attempts
  "spawnChance": 0.4,          // 40% chance to spawn when timer triggers
  "despawnTimeMinutes": 30.0,  // How long uncommon comets last before despawning
  "minSpawnDistance": 30,      // Minimum blocks from player
  "maxSpawnDistance": 50,      // Maximum blocks from player
  "globalComets": false        // If true, any player can trigger any comet
}
```

**zoneSpawnChances** - Tier distribution per zone
```json
"zoneSpawnChances": {
  "0": { "tier1": 1.0, "tier2": 0.0, "tier3": 0.0, "tier4": 0.0 },
  "1": { "tier1": 0.8, "tier2": 0.2, "tier3": 0.0, "tier4": 0.0 }
}
```

**tierSettings** - Per-tier combat settings
```json
"tierSettings": {
  "1": {
    "timeoutSeconds": 90,    // How long before wave times out
    "minRadius": 3.0,        // Min spawn radius for enemies
    "maxRadius": 5.0         // Max spawn radius for enemies
  }
}
```

**rewardSettings** - Loot drops per tier
```json
"rewardSettings": {
  "1": {
    "drops": [
      {
        "id": "Ingredient_Bar_Copper",
        "minCount": 5,
        "maxCount": 7,
        "chance": 100,
        "displayName": "Copper Ingots"
      }
    ]
  }
}
```

**themes** - Enemy wave configurations (see existing themes for examples)

### Theme Configuration

Themes can have custom reward overrides that replace the default tier rewards:

```json
"skeleton_siege": {
  "displayName": "Skeleton Siege",
  "testOnly": true,           // Won't spawn naturally, only via command
  "tiers": [2, 3],
  "waves": [...],
  "rewardOverride": {
    "2": {
      "drops": [...],
      "bonusDrops": [...]
    }
  }
}
```

Use `"testOnly": true` on themes you're testing to prevent them from spawning naturally.

### Creating Custom Themes

Want to make your own encounters? Check the `skeleton_siege` theme at the bottom of `comet_config.json` for a full example. You can:

- Define multiple waves with different enemy compositions
- Set per-tier stats for each mob (HP, damage, scale, speed)
- Configure boss waves separately from normal waves
- Override the default loot table with custom rewards per tier
- Use `"testOnly": true` to prevent a theme from spawning naturally while you test it

The config is fully JSON - just copy an existing theme, rename it, and start tweaking.

## Source Code

Source is available at [INSERT REPO LINK HERE]

## Bug Reports

Found a bug? Report it at [INSERT ISSUE TRACKER LINK HERE] or reach out directly. Include what you were doing, any error messages from the server console, and whether you're running singleplayer or multiplayer.

## Usage & Distribution

This mod is free to use, modify, and redistribute. Just credit me (Frog) somewhere if you share it or use it in your own projects. That's all I ask.

## Credits

Created by **Frog**

Some parts of this mod were made with AI assistance - mainly coding help and upscaling some visual assets like the mod icon.

---

Have fun getting obliterated by Legendary comets.
