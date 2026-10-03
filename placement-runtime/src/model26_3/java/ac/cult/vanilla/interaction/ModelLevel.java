package ac.cult.vanilla.interaction;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;

/** The 26.3 Level supplies clocks rather than the earlier brewing and fuel services. */
abstract class ModelLevel extends Level {
    ModelLevel(
            WritableLevelData data,
            ResourceKey<Level> dimension,
            RegistryAccess registries,
            Holder<DimensionType> type,
            boolean client,
            boolean debug,
            long seed,
            int neighbors) {
        super(data, dimension, registries, type, client, debug, seed, neighbors);
    }

    @Override
    public ClockManager clockManager() {
        return clock -> new ClockInstance() {
            public long totalTicks() {
                return 0;
            }

            public float partialTick() {
                return 0;
            }

            public float rate() {
                return 1;
            }

            public boolean isPaused() {
                return false;
            }
        };
    }
}
