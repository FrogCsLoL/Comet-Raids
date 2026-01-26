package com.cometmod;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nullable;

/**
 * Uses the game's native CollisionModule.validatePosition to avoid spawning
 * mobs inside blocks (suffocation). Same logic as ActionSpawn, NPCTestCommand,
 * and SpawningContext.
 */
public final class CometSpawnUtil {

    /**
     * Conservative NPC bounding box (entity-local: feet at y=0).
     * 0.7x1.9x0.7 to cover typical humanoid mobs; used when we don't have
     * the actual model bbox.
     */
    private static final Box DEFAULT_NPC_BOX = new Box(-0.35, 0, -0.35, 0.35, 1.9, 0.35);

    /** Offsets (dx, dy, dz) to try when finding a valid mob spawn. */
    private static final int[][] MOB_SPAWN_OFFSETS = {
        { 0, 0, 0 },
        { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
        { 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 },
        { 0, 1, 0 }
    };

    private CometSpawnUtil() {}

    /**
     * Check if a mob can stand at (x,y,z) using the game's CollisionModule:
     * the NPC box is tested for block overlap (validatePosition returns -1
     * when overlapping). Matches ActionSpawn, SpawningContext, NPCTestCommand.
     */
    public static boolean isValidMobSpawn(World world, double x, double y, double z) {
        try {
            CollisionModule cm = CollisionModule.get();
            if (cm == null || cm.isDisabled()) return true; // fallback: allow
            CollisionResult res = new CollisionResult();
            int v = cm.validatePosition(world, DEFAULT_NPC_BOX, new Vector3d(x, y, z), res);
            return v != -1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find a valid mob spawn near the preferred position by trying offsets.
     */
    @Nullable
    public static Vector3d findValidMobSpawn(World world, Vector3d preferred, int maxRetries) {
        for (int i = 0; i < Math.min(maxRetries, MOB_SPAWN_OFFSETS.length); i++) {
            int[] o = MOB_SPAWN_OFFSETS[i];
            double x = preferred.x + o[0];
            double y = preferred.y + o[1];
            double z = preferred.z + o[2];
            if (isValidMobSpawn(world, x, y, z))
                return new Vector3d(x, y, z);
        }
        return null;
    }
}
