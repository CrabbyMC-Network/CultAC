package ac.cult.vanilla.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;

/** 1.21.11 ClientPacketListener creates these services from the received registries and features. */
abstract class ModelLevel extends Level {
    private final FuelValues fuels;
    private final PotionBrewing brewing;

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
        fuels = FuelValues.vanillaBurnTimes(registries, FeatureFlags.DEFAULT_FLAGS);
        brewing = PotionBrewing.bootstrap(FeatureFlags.DEFAULT_FLAGS);
    }

    @Override
    public FuelValues fuelValues() {
        return fuels;
    }

    @Override
    public PotionBrewing potionBrewing() {
        return brewing;
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        boolean nether = dimensionType().cardinalLightType() == DimensionType.CardinalLightType.NETHER;
        if (!shaded) return nether ? 0.9F : 1.0F;
        return switch (direction) {
            case DOWN -> nether ? 0.9F : 0.5F;
            case UP -> nether ? 0.9F : 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        throw new UnsupportedOperationException("The compensated interaction world does not render block colors");
    }
}
