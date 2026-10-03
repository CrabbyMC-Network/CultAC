package ac.cult.cultac.utils.change;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public record BlockModification(
        BlockState oldBlockContents, BlockState newBlockContents, BlockPos location, int tick, Cause cause) {
    @Override
    public @NotNull String toString() {
        return String.format(
                "BlockModification{location=%s, old=%s, new=%s, tick=%d, cause=%s}",
                location, oldBlockContents, newBlockContents, tick, cause);
    }

    public enum Cause {
        START_DIGGING,
        APPLY_BLOCK_CHANGES,
        HANDLE_NETTY_SYNC_TRANSACTION,
        OTHER
    }
}
