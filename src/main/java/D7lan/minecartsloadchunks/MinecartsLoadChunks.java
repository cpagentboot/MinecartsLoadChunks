package D7lan.minecartsloadchunks;

import D7lan.minecartsloadchunks.world.WorldChunksManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class MinecartsLoadChunks implements ModInitializer {
    private final Map<UUID, Long> MINECART_LAST_MOVED = new WeakHashMap<>();
    private final Map<ServerWorld, Long> WORLD_LAST_SAVED = new HashMap<>();

    private EntityType<?>[] MINECART_TYPES;
    private static ModConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = ModConfig.loadConfig();
        MINECART_TYPES = getConfig().getMinecartTypes();

        if (getConfig().loadChunks) {
            ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
        }
    }

    private void onServerTick(MinecraftServer server) {
        long currentTick = server.getTicks();

        for (ServerWorld world : server.getWorlds()) {
            WorldChunksManager.loadPersistentChunksForWorld(world);

            for (EntityType<?> type : MINECART_TYPES) {
                for (AbstractMinecartEntity minecart : world.getEntitiesByType((EntityType<AbstractMinecartEntity>) type, e -> true)) {
                    if (!getConfig().alwaysLoad) {
                        boolean isMoving = minecart.getVelocity().lengthSquared() > 1e-6;
                        if (isMoving) MINECART_LAST_MOVED.put(minecart.getUuid(), currentTick);

                        Long lastMoveTick = MINECART_LAST_MOVED.get(minecart.getUuid());
                        boolean shouldLoad = lastMoveTick != null && (currentTick - lastMoveTick) <= (long) getConfig().movementDuration * 20;
                        if (!shouldLoad) continue;
                    }

                    if (getConfig().smartLoad) {
                        Vec3d pos = new Vec3d(minecart.getX(), minecart.getY(), minecart.getZ());
                        Vec3d vel = minecart.getVelocity();
                        double x = pos.x + vel.x;
                        double z = pos.z + vel.z;

                        int chunkX = (int)(x / 16);
                        int chunkZ = (int)(z / 16);
                        if (x < 0) chunkX--;
                        if (z < 0) chunkZ--;
                        ChunkPos nextChunkPos = new ChunkPos(chunkX, chunkZ);

                        WorldChunksManager.forceLoadChunk(world, minecart.getChunkPos());
                        WorldChunksManager.forceLoadChunk(world, nextChunkPos);
                    } else {
                        ChunkPos centerChunk = new ChunkPos(minecart.getBlockPos());
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                                WorldChunksManager.forceLoadChunk(world, cp);
                            }
                        }
                    }
                }
            }

            WorldChunksManager.freeChunks(world);

            // Save this world's persistent data every 5 seconds (100 ticks).
            long lastSaveTick = WORLD_LAST_SAVED.getOrDefault(world, 0L);
            if (currentTick - lastSaveTick >= 5 * 20) {
                WorldChunksManager.savePersistentChunksForWorld(world);
                WORLD_LAST_SAVED.put(world, currentTick);
            }
        }
    }

    public static ModConfig getConfig() {
        return CONFIG;
    }
}
