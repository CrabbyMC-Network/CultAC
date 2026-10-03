package ac.cult.cultac.utils.nmsutil;

import ac.cult.cultac.protocol.value.Direction;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class NmsBlockTags {
    private static final TagKey<Block> BARS = barsTag();

    @SuppressWarnings("unchecked")
    private static TagKey<Block> barsTag() {
        try {
            return (TagKey<Block>) BlockTags.class.getField("BARS").get(null);
        } catch (NoSuchFieldException legacy) {
            // Before the bars tag existed, IronBarsBlock supplies the same family.
            return null;
        } catch (IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private NmsBlockTags() {}

    public static BlockState toNmsState(BlockState state) {
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    public static int getInt(BlockState state, IntegerProperty property, int fallback) {
        return state.hasProperty(property) ? state.getValue(property) : fallback;
    }

    public static boolean getBoolean(BlockState state, BooleanProperty property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    public static boolean hasDirection(BlockState state, Direction face) {
        BooleanProperty property = directionProperty(face);
        return property != null && getBoolean(state, property);
    }

    public static Direction getFacing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return toBukkitFace(state.getValue(BlockStateProperties.FACING));
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return toBukkitFace(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return Direction.UP;
    }

    private static Direction toBukkitFace(net.minecraft.core.Direction direction) {
        return switch (direction) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    private static BooleanProperty directionProperty(Direction face) {
        return switch (face) {
            case DOWN -> BlockStateProperties.DOWN;
            case UP -> BlockStateProperties.UP;
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case EAST -> BlockStateProperties.EAST;
            default -> null;
        };
    }

    public static Block toNmsBlock(Block block) {
        return block;
    }

    public static Item toNmsItem(Block block) {
        return block == null ? null : block.asItem();
    }

    public static String name(Block block) {
        return NmsIdentifierUtil.registryPath(net.minecraft.core.registries.BuiltInRegistries.BLOCK, block)
                .toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isWater(BlockState data) {
        return toNmsState(data).getFluidState().getType().isSame(Fluids.WATER);
    }

    public static boolean isWaterSource(BlockState data) {
        FluidState fluidState = toNmsState(data).getFluidState();
        return fluidState.isSourceOfType(Fluids.WATER);
    }

    public static boolean isNoPlaceLiquid(Block material) {
        Block block = toNmsBlock(material);
        return block instanceof LiquidBlock liquidBlock
                && liquidBlock.defaultBlockState().getFluidState().isSource();
    }

    public static boolean isReplaceable(Block material) {
        Block block = toNmsBlock(material);
        return block == null
                || block.defaultBlockState().isAir()
                || block.defaultBlockState().canBeReplaced();
    }

    public static boolean isShapeExceedsCube(Block material) {
        Block block = toNmsBlock(material);
        return block != null && block.defaultBlockState().hasLargeCollisionShape();
    }

    public static boolean isConnectingBlock(Block material) {
        Block block = toNmsBlock(material);
        if (block == null) {
            return false;
        }
        BlockState state = block.defaultBlockState();

        return isStairs(state)
                || isWall(block, state)
                || isFence(block, state)
                || isFenceGate(block, state)
                || BARS != null && state.is(BARS)
                || block instanceof IronBarsBlock
                || state.hasProperty(BlockStateProperties.BELL_ATTACHMENT)
                || state.hasProperty(BlockStateProperties.TILT)
                || block instanceof BigDripleafStemBlock
                || material == Blocks.POINTED_DRIPSTONE
                || block instanceof ChorusPlantBlock
                || state.hasProperty(BlockStateProperties.CHEST_TYPE);
    }

    public static boolean hasBlockTag(Block material, TagKey<Block> tag) {
        Block block = toNmsBlock(material);
        return tag != null && block != null && block.defaultBlockState().is(tag);
    }

    public static Set<Block> blockValues(TagKey<Block> tag) {
        if (tag == null) {
            return Set.of();
        }

        Set<Block> resolved = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .filter(material -> hasBlockTag(material, tag))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(resolved);
    }

    public static boolean isFence(Block material) {
        Block block = toNmsBlock(material);
        return block != null && isFence(block, block.defaultBlockState());
    }

    public static boolean isWall(Block material) {
        Block block = toNmsBlock(material);
        return block != null && isWall(block, block.defaultBlockState());
    }

    public static boolean isFenceGate(Block material) {
        Block block = toNmsBlock(material);
        return block != null && isFenceGate(block, block.defaultBlockState());
    }

    public static boolean isSlab(Block material) {
        Block block = toNmsBlock(material);
        return block != null && (block.defaultBlockState().is(BlockTags.SLABS) || block instanceof SlabBlock);
    }

    public static boolean isSlab(BlockState state) {
        return state != null && (state.is(BlockTags.SLABS) || state.getBlock() instanceof SlabBlock);
    }

    public static boolean isTrapdoor(Block material) {
        Block block = toNmsBlock(material);
        return block != null && (block.defaultBlockState().is(BlockTags.TRAPDOORS) || block instanceof TrapDoorBlock);
    }

    public static boolean isTrapdoor(BlockState state) {
        return state != null && (state.is(BlockTags.TRAPDOORS) || state.getBlock() instanceof TrapDoorBlock);
    }

    public static boolean isDoor(Block material) {
        Block block = toNmsBlock(material);
        return block != null && (block.defaultBlockState().is(BlockTags.DOORS) || block instanceof DoorBlock);
    }

    public static boolean isBed(Block material) {
        Block block = toNmsBlock(material);
        return block != null && (block.defaultBlockState().is(BlockTags.BEDS) || block instanceof BedBlock);
    }

    public static boolean isShulkerBox(Block material) {
        Block block = toNmsBlock(material);
        return block != null
                && (block.defaultBlockState().is(BlockTags.SHULKER_BOXES) || block instanceof ShulkerBoxBlock);
    }

    public static boolean isShulkerBox(BlockState state) {
        return state != null && (state.is(BlockTags.SHULKER_BOXES) || state.getBlock() instanceof ShulkerBoxBlock);
    }

    private static boolean isStairs(BlockState state) {
        return state.is(BlockTags.STAIRS);
    }

    private static boolean isFence(Block block, BlockState state) {
        return state.is(BlockTags.FENCES) || block instanceof FenceBlock;
    }

    private static boolean isWall(Block block, BlockState state) {
        return state.is(BlockTags.WALLS) || block instanceof WallBlock;
    }

    private static boolean isFenceGate(Block block, BlockState state) {
        return state.is(BlockTags.FENCE_GATES) || block instanceof FenceGateBlock;
    }
}
