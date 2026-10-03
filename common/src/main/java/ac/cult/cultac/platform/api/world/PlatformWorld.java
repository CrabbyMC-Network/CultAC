package ac.cult.cultac.platform.api.world;

import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface PlatformWorld {
    boolean isChunkLoaded(int chunkX, int chunkZ);

    BlockState getBlockAt(int x, int y, int z);

    String getName();

    @Nullable
    UUID getUID();

    PlatformChunk getChunkAt(int currChunkX, int currChunkZ);

    boolean isLoaded();
}
