package D7lan.minecartsloadchunks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;


public class Minecartsloadchunks implements ModInitializer {
    // A map to track the last tick at which each minecart was seen moving.
    private final Map<UUID, Long> minecartMovementMap = new WeakHashMap<>();

    // A map to record which chunks (per world) have been forced to load,
    // along with the tick at which they should be released.
    private final Map<ServerWorld, Map<ChunkPos, Long>> forcedChunks = new HashMap<>();

    // Constants for time intervals (in ticks; 20 ticks = 1 second)
    private static final long THIRTY_SECONDS = 30 * 20; // 600 ticks
    private static final long ONE_MINUTE = 60 * 20;       // 1200 ticks

    @Override
    public void onInitialize() {
        // Register a server tick callback to run our logic each tick.
        ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        long currentTick = server.getTicks();

        // Process each loaded world.
        for (ServerWorld world : server.getWorlds()) {
            // Ensure we have a forcedChunks map for this world.
            forcedChunks.computeIfAbsent(world, w -> new HashMap<>());
            Map<ChunkPos, Long> worldForcedChunks = forcedChunks.get(world);

            // Update our minecart movement record.
            // For every minecart in the world, if it has nonzero velocity, record the tick.
            for (AbstractMinecartEntity minecart : world.getEntitiesByType(
                    EntityType.MINECART, minecart -> true)) {
                Vec3d velocity = minecart.getVelocity();
                // Using a very small threshold to decide if the minecart is “moving”
                if (velocity.lengthSquared() > 1e-6) {
                    System.out.printf("Minecart moving at (%.2f, %.2f, %.2f) with velocity (%.2f, %.2f, %.2f)%n",
                            minecart.getX(), minecart.getY(), minecart.getZ(),
                            velocity.x, velocity.y, velocity.z);
                    minecartMovementMap.put(minecart.getUuid(), currentTick);
                }
            }

            // Now, for each minecart that has moved in the last 30 seconds, force-load a 3x3 grid.
            for (AbstractMinecartEntity minecart : world.getEntitiesByType(
                    EntityType.MINECART, minecart -> true)) {
                Long lastMoveTick = minecartMovementMap.get(minecart.getUuid());
                if (lastMoveTick != null && (currentTick - lastMoveTick) <= THIRTY_SECONDS) {
                    // Determine the minecart's current chunk position.
                    ChunkPos centerChunk = new ChunkPos(minecart.getBlockPos());
                    // For the 3x3 grid centered on the minecart’s chunk:
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                            // If this chunk hasn’t already been forced, force it.
                            // (We rely on our worldForcedChunks map for tracking.)
                            if (!worldForcedChunks.containsKey(cp)) {
                                System.out.printf("Minecart loaded chunk at (%d, %d)%n", cp.x, cp.z);
                                world.setChunkForced(cp.x, cp.z, true);
                            }
                            // Update the expiry time for this chunk to one minute from now.
                            long newExpiry = currentTick + ONE_MINUTE;
                            // If the chunk is already recorded, extend the expiry if necessary.
                            worldForcedChunks.merge(cp, newExpiry, Math::max);
                        }
                    }
                }
            }

            // Finally, check for any forced chunks whose expiry time has passed.
            Iterator<Map.Entry<ChunkPos, Long>> iterator = worldForcedChunks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ChunkPos, Long> entry = iterator.next();
                if (currentTick > entry.getValue()) {
                    // The chunk's forced load period has expired – release it.
                    ChunkPos cp = entry.getKey();
                    world.setChunkForced(cp.x, cp.z, false);
                    iterator.remove();
                }
            }
        }
    }
}