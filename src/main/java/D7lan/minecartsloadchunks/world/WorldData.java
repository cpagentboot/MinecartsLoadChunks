package D7lan.minecartsloadchunks.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldData {
    protected final ConcurrentHashMap<ChunkPos, ChunkData> chunkData = new ConcurrentHashMap<>();
    private final ServerWorld world;
    public WorldData(ServerWorld world) {
        this.world = world;
    }

    public Optional<ChunkData> getChunkData(ChunkPos pos) {
        return Optional.ofNullable(chunkData.get(pos));
    }

    public Collection<ChunkData> getChunks() {
        return chunkData.values();
    }

    public ChunkData addForcedChunk(ChunkData chunkData) {
        return this.chunkData.put(chunkData.pos, chunkData);
    }

    public ChunkData unloadChunk(ChunkPos pos) {
        ChunkData data = this.chunkData.remove(pos);
        if (data != null) {
            this.world.setChunkForced(pos.x, pos.z, false);
        }
        return data;
    }
}