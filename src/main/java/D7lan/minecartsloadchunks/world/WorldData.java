package D7lan.minecartsloadchunks.world;

import net.minecraft.util.math.ChunkPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;

public class WorldData {
    private final HashMap<ChunkPos, ChunkData> chunkData = new HashMap<>();

    public Optional<ChunkData> getChunkData(ChunkPos pos) {
        return Optional.ofNullable(chunkData.get(pos));
    }

    public Collection<ChunkData> getChunks() {
        return chunkData.values();
    }

    public ChunkData addForcedChunk(ChunkData chunkData) {
        return this.chunkData.put(chunkData.pos, chunkData);
    }
}