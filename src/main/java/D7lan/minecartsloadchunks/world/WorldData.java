package D7lan.minecartsloadchunks.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class WorldData {
    protected final HashMap<ChunkPos, ChunkData> chunkData = new HashMap<>();
    private final ServerWorld world;
    public WorldData(ServerWorld world) {
        this.world = world;
    }

    public Optional<ChunkData> getChunkData(ChunkPos pos) {
        synchronized (chunkData) {
            return Optional.ofNullable(chunkData.get(pos));
        }
    }

    public Collection<ChunkData> getChunks() {
        synchronized (chunkData) {
            return chunkData.values();
        }
    }

    public ChunkData addForcedChunk(ChunkData chunkData) {
        synchronized (this.chunkData) {
            return this.chunkData.put(chunkData.pos, chunkData);
        }
    }

    public ChunkData unloadChunk(ChunkPos pos) {
        synchronized (chunkData) {
            ChunkData data = this.chunkData.remove(pos);
            if (data != null) {
                this.world.setChunkForced(pos.x, pos.z, false);
            }
            return data;
        }
    }
}