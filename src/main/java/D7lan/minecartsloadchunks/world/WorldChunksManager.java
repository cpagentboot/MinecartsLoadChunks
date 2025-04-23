package D7lan.minecartsloadchunks.world;

import D7lan.minecartsloadchunks.MinecartsLoadChunks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class WorldChunksManager {
    private static final Map<ServerWorld, WorldData> WORLD_DATA = new HashMap<>();

    /**
     * Returns the persistent file for a given world.
     * @param world target world
     * @return the world's persistent file
     */
    public static Optional<File> getPersistentFileForWorld(ServerWorld world) {
        MinecraftServer server = world.getServer();

        // Get the world's save directory using WorldSavePath.ROOT.
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        String worldId = world.getRegistryKey().getValue().getPath();

        File worldFolder = new File(worldRoot.toFile(), worldId);
        if (!worldFolder.exists()) {
            if (!worldFolder.mkdirs()) return Optional.empty();
        }
        return Optional.of(new File(worldFolder, "minecartsloadchunks_forced_chunks.json"));
    }

    /**
     * Loads persistent forced-chunk data for a given world.
     * @param world target world
     * @return loaded chunks
     */
    public static int loadPersistentChunksForWorld(ServerWorld world) {
        int loadedCount = 0;
        if (WORLD_DATA.containsKey(world)) return loadedCount; // already loaded

        MinecraftServer server = world.getServer();
        long currentTick = server.getTicks();

        Optional<File> optionalFile = getPersistentFileForWorld(world);
        if (optionalFile.isEmpty() || !optionalFile.get().exists()) {
            System.out.printf("Loaded %d forceloaded chunks for world %s.%n", loadedCount, world.getRegistryKey().getValue());
            WORLD_DATA.put(world, new WorldData(world));
            return loadedCount;
        }

        File persistentFile = optionalFile.get();
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(persistentFile)) {
            ChunkData[] chunksToLoad = gson.fromJson(reader, ChunkData[].class);
            if (chunksToLoad == null) return loadedCount;

            WorldData worldData = WORLD_DATA.getOrDefault(world, new WorldData(world));
            for (ChunkData chunkData : chunksToLoad) {
                // [!] When the player closes the world, the ticks are reset.
                // In simple terms, this is needed because:
                //
                // Suppose there's a chunk that should expire in tick 500
                // the world is in the tick 300 and the player leaves the world.
                // When he rejoins, the tick's back to 1.
                //
                // So, instead of keeping the chunk to expire at tick 500,
                // we get the expiry tick (500), minus the saved tick (300)
                // that is what remained for the chunk to be unloaded
                // so we get the currentTick + what remained, and there we go
                System.out.println("BEFORE: " + chunkData.expiryTick);
                chunkData.expiryTick = currentTick + chunkData.expiryTick - chunkData.savedTick;
                System.out.println("AFTER: " + chunkData.expiryTick);

                boolean isChunkForced = chunkData.expiryTick > currentTick;
                world.setChunkForced(chunkData.pos.x, chunkData.pos.z, isChunkForced);

                if (isChunkForced) {
                    worldData.addForcedChunk(chunkData);
                    loadedCount++;
                    System.out.printf("Restored forced chunk at (%d, %d) in world %s%n", chunkData.pos.x, chunkData.pos.z, chunkData.worldId);
                }
            }
            WORLD_DATA.put(world, worldData);

            System.out.printf("Loaded %d forceloaded chunks for world %s.%n", loadedCount, world.getRegistryKey().getValue());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadedCount;
    }

    /**
     * Saves persistent forced-chunk data for a given world.
     * @param world target world
     * @return loaded chunks
     */
    public static int savePersistentChunksForWorld(ServerWorld world) {
        long currentTick = world.getServer().getTicks();

        WorldData worldData = WORLD_DATA.getOrDefault(world, new WorldData(world));
        Collection<ChunkData> forcedChunks = worldData.getChunks();

        for (ChunkData data : forcedChunks) {
            data.savedTick = currentTick;
        }

        Optional<File> optionalFile = getPersistentFileForWorld(world);
        if (optionalFile.isEmpty()) {
            System.out.println("[!] [MinecartsLoadChunks] Could not save world, since the file could not be created.");
            return 0;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(optionalFile.get())) {
            gson.toJson(forcedChunks, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (MinecartsLoadChunks.getConfig().spamConsole) {
            System.out.printf("Saved %d forceloaded chunks for world %s.%n", forcedChunks.size(), world.getRegistryKey().getValue());
        }
        return forcedChunks.size();
    }

    /**
     * Sets a chunk to be forcefully loaded
     * @param world target world
     * @param pos chunk position
     */
    public static void forceLoadChunk(ServerWorld world, ChunkPos pos) {
        if (!WORLD_DATA.containsKey(world)) return; // world not loaded yet

        WorldData worldData = WORLD_DATA.get(world);
        long currentTick = world.getServer().getTicks();

        long expiryTick = currentTick + (long) MinecartsLoadChunks.getConfig().cartLoadDuration * 20;
        if (worldData.addForcedChunk(new ChunkData(world.getRegistryKey().toString(), pos, expiryTick, currentTick)) == null) {
            world.setChunkForced(pos.x, pos.z, true);
        }
    }

    /**
     * Unsets chunks to be forcefully loaded if the time has expired.
     * @param world target world
     */
    public static void freeChunks(ServerWorld world) {
        if (!WORLD_DATA.containsKey(world)) return;
        WorldData worldData = WORLD_DATA.get(world);
        long currentTick = world.getServer().getTicks();

        synchronized (worldData.chunkData) {
            Iterator<ChunkData> iterator = worldData.getChunks().iterator();
            while (iterator.hasNext()) {
                ChunkData data = iterator.next();
                if (currentTick > data.expiryTick) {
                    world.setChunkForced(data.pos.x, data.pos.z, false);
                    iterator.remove();
                }
            }
        }
    }
}
