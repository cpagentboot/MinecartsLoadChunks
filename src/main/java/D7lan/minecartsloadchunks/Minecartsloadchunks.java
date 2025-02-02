package D7lan.minecartsloadchunks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Minecartsloadchunks implements ModInitializer {
    // Track the last tick each minecart was seen moving.
    private final Map<UUID, Long> minecartMovementMap = new WeakHashMap<>();

    // For each world, record forced chunks (with expiry tick).
    private final Map<ServerWorld, Map<ChunkPos, Long>> forcedChunks = new HashMap<>();

    // Timing constants (in ticks; 20 ticks = 1 second).
    private static final long THIRTY_SECONDS = 30 * 20; // 600 ticks
    private static final long ONE_MINUTE = 60 * 20;       // 1200 ticks

    // For each world, store the last tick when the persistent data was saved.
    private final Map<ServerWorld, Long> lastSaveTickMap = new HashMap<>();

    // Track which worlds have loaded their persistent data.
    private final Set<ServerWorld> persistentLoadedWorlds = new HashSet<>();

    // Define the minecart types we want to process.
    // (Ensure that these types exist in your targeted version of Minecraft.)
    private static final EntityType<?>[] MINECART_TYPES = new EntityType<?>[] {
            EntityType.MINECART,
            EntityType.CHEST_MINECART,
            EntityType.FURNACE_MINECART,
            EntityType.HOPPER_MINECART,
            EntityType.TNT_MINECART,
            EntityType.COMMAND_BLOCK_MINECART
    };

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        long currentTick = server.getTicks();

        // Process each loaded world.
        for (ServerWorld world : server.getWorlds()) {
            // Load persistent data for this world if not already loaded.
            if (!persistentLoadedWorlds.contains(world)) {
                loadPersistentChunksForWorld(world, currentTick, server);
                persistentLoadedWorlds.add(world);
            }

            // Ensure we have a forcedChunks map for this world.
            forcedChunks.computeIfAbsent(world, w -> new HashMap<>());
            Map<ChunkPos, Long> worldForcedChunks = forcedChunks.get(world);

            // First loop: Update minecart movement record for each minecart type.
            for (EntityType<?> type : MINECART_TYPES) {
                // We cast safely to AbstractMinecartEntity.
                for (AbstractMinecartEntity minecart : world.getEntitiesByType((EntityType<AbstractMinecartEntity>) type, e -> true)) {
                    Vec3d velocity = minecart.getVelocity();
                    if (velocity.lengthSquared() > 1e-6) {
                        minecartMovementMap.put(minecart.getUuid(), currentTick);
                    }
                }
            }

            // Second loop: For each minecart that has moved recently, force-load a 3x3 grid.
            for (EntityType<?> type : MINECART_TYPES) {
                for (AbstractMinecartEntity minecart : world.getEntitiesByType((EntityType<AbstractMinecartEntity>) type, e -> true)) {
                    Long lastMoveTick = minecartMovementMap.get(minecart.getUuid());
                    if (lastMoveTick != null && (currentTick - lastMoveTick) <= THIRTY_SECONDS) {
                        // Determine the minecart's current chunk.
                        ChunkPos centerChunk = new ChunkPos(minecart.getBlockPos());
                        // Force-load a 3x3 grid around the minecart's chunk.
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                                if (!worldForcedChunks.containsKey(cp)) {
                                    System.out.printf("Minecart (%s) loaded chunk at (%d, %d) in world %s%n",
                                            type.getTranslationKey(), cp.x, cp.z, world.getRegistryKey().getValue());
                                    world.setChunkForced(cp.x, cp.z, true);
                                }
                                long newExpiry = currentTick + ONE_MINUTE;
                                worldForcedChunks.merge(cp, newExpiry, Math::max);
                            }
                        }
                    }
                }
            }

            // Release chunks whose forced period has expired.
            Iterator<Map.Entry<ChunkPos, Long>> iterator = worldForcedChunks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ChunkPos, Long> entry = iterator.next();
                if (currentTick > entry.getValue()) {
                    ChunkPos cp = entry.getKey();
                    world.setChunkForced(cp.x, cp.z, false);
                    iterator.remove();
                }
            }

            // Save this world's persistent data every 5 seconds (100 ticks).
            long lastSaveTick = lastSaveTickMap.getOrDefault(world, 0L);
            if (currentTick - lastSaveTick >= 5 * 20) {
                savePersistentChunksForWorld(world, currentTick, server);
                lastSaveTickMap.put(world, currentTick);
            }
        }
    }

    // Loads persistent forced-chunk data for a given world.
    private void loadPersistentChunksForWorld(ServerWorld world, long currentTick, MinecraftServer server) {
        File persistentFile = getPersistentFileForWorld(world, server);
        int loadedCount = 0;
        if (!persistentFile.exists()) {
            System.out.printf("Loaded %d forceloaded chunks for world %s.%n",
                    loadedCount, world.getRegistryKey().getValue());
            return;
        }
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(persistentFile)) {
            PersistentChunkData[] data = gson.fromJson(reader, PersistentChunkData[].class);
            if (data != null) {
                forcedChunks.computeIfAbsent(world, w -> new HashMap<>());
                Map<ChunkPos, Long> worldForcedChunks = forcedChunks.get(world);
                for (PersistentChunkData pcd : data) {
                    // Adjust the expiry based on how many ticks remained when saved.
                    long adjustedExpiry = currentTick + (pcd.expiryTick - pcd.savedTick);
                    if (adjustedExpiry > currentTick) {
                        ChunkPos cp = new ChunkPos(pcd.chunkX, pcd.chunkZ);
                        worldForcedChunks.put(cp, adjustedExpiry);
                        world.setChunkForced(cp.x, cp.z, true);
                        loadedCount++;
                        System.out.printf("Restored forced chunk at (%d, %d) in world %s%n",
                                cp.x, cp.z, pcd.worldId);
                    }
                }
            }
            System.out.printf("Loaded %d forceloaded chunks for world %s.%n",
                    loadedCount, world.getRegistryKey().getValue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Saves persistent forced-chunk data for a given world.
    private void savePersistentChunksForWorld(ServerWorld world, long currentTick, MinecraftServer server) {
        Map<ChunkPos, Long> worldForcedChunks = forcedChunks.get(world);
        if (worldForcedChunks == null || worldForcedChunks.isEmpty()) {
            // Optionally, delete the file if it exists.
            File persistentFile = getPersistentFileForWorld(world, server);
            if (persistentFile.exists()) {
                persistentFile.delete();
            }
            System.out.printf("Saved %d forceloaded chunks for world %s.%n",
                    0, world.getRegistryKey().getValue());
            return;
        }
        List<PersistentChunkData> list = new ArrayList<>();
        String worldId = world.getRegistryKey().getValue().toString();
        for (Map.Entry<ChunkPos, Long> entry : worldForcedChunks.entrySet()) {
            PersistentChunkData pcd = new PersistentChunkData();
            pcd.worldId = worldId;
            pcd.chunkX = entry.getKey().x;
            pcd.chunkZ = entry.getKey().z;
            pcd.expiryTick = entry.getValue();
            pcd.savedTick = currentTick;
            list.add(pcd);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File persistentFile = getPersistentFileForWorld(world, server);
        persistentFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(persistentFile)) {
            gson.toJson(list, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.printf("Saved %d forceloaded chunks for world %s.%n",
                list.size(), world.getRegistryKey().getValue());
    }

    // Returns the persistent file for a given world.
    private File getPersistentFileForWorld(ServerWorld world, MinecraftServer server) {
        // Get the world's save directory using WorldSavePath.ROOT.
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        // Use the world's registry key (its path value) as the subfolder name.
        String worldId = world.getRegistryKey().getValue().getPath();
        File worldFolder = new File(worldRoot.toFile(), worldId);
        if (!worldFolder.exists()) {
            worldFolder.mkdirs();
        }
        return new File(worldFolder, "minecartsloadchunks_forced_chunks.json");
    }

    // Data structure for persistent storage.
    private static class PersistentChunkData {
        String worldId;
        int chunkX;
        int chunkZ;
        long expiryTick; // The tick at which this chunk was set to expire.
        long savedTick;  // The server tick when the data was saved.
    }
}
