package ac.cult.cultac.utils.minecraft;

import ac.cult.cultac.utils.latency.CompensatedWorld;
import ac.cult.placement.api.PlacementEngine;
import java.util.Objects;
import net.minecraft.world.level.block.Block;

/** Packet-compensated world only; no live server reads cross the vanilla boundary. */
public class CompensatedBlockView implements PlacementEngine.World {
    private final CompensatedWorld world;

    public CompensatedBlockView(CompensatedWorld world) {
        this.world = Objects.requireNonNull(world);
    }

    @Override
    public int stateAt(int x, int y, int z) {
        return Block.getId(world.getBlockStateAt(x, y, z));
    }

    @Override
    public int minY() {
        return world.getMinY();
    }

    @Override
    public int height() {
        return world.getHeight();
    }

    @Override
    public boolean loaded(int x, int z) {
        return world.isChunkLoaded(x, z);
    }

    @Override
    public int brightness(int x, int y, int z) {
        return world.getRawBrightness(x, y, z);
    }
}
