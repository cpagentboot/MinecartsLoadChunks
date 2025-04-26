package D7lan.minecartsloadchunks.world;

import net.minecraft.util.math.ChunkPos;

public class ChunkData {
    public final String worldId;
    public final ChunkPos pos;
    public long expiryTick; // The tick at which this chunk was set to expire.
    public long savedTick;  // The server tick when the data was saved.
    public ChunkData(String worldId, ChunkPos pos, long expiryTick, long savedTick) {
        this.worldId = worldId;
        this.pos = pos;
        this.expiryTick = expiryTick;
        this.savedTick = savedTick;
    }
}