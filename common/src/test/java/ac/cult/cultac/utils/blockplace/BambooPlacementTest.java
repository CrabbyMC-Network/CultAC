package ac.cult.cultac.utils.blockplace;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BambooPlacementTest {
    private static final BlockPos PLACED = new BlockPos(0, 64, 0);

    @BeforeClass
    public static void bootstrap() {
        OfflineCultTestBootstrap.installConfig();
    }

    @Test
    public void supportedGroundProducesSaplingAndConsumesItem() {
        World world = new World();
        world.blocks.put(PLACED.below(), Blocks.DIRT.defaultBlockState());
        assertTrue(placedState(world).is(Blocks.BAMBOO_SAPLING));
    }

    @Test
    public void saplingSupportProducesYoungStalk() {
        World world = new World();
        world.blocks.put(PLACED.below(), Blocks.BAMBOO_SAPLING.defaultBlockState());
        BlockState state = placedState(world);
        assertTrue(state.is(Blocks.BAMBOO));
        assertEquals(Integer.valueOf(0), state.getValue(BambooStalkBlock.AGE));
    }

    @Test
    public void stalkSupportPreservesBothAges() {
        for (int age : new int[]{0, 1}) {
            World world = new World();
            world.blocks.put(PLACED.below(), Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.AGE, age));
            BlockState state = placedState(world);
            assertTrue(state.is(Blocks.BAMBOO));
            assertEquals(Integer.valueOf(age), state.getValue(BambooStalkBlock.AGE));
        }
    }

    @Test
    public void fillingGapCopiesStalkAgeAbove() {
        for (int age : new int[]{0, 1}) {
            World world = new World();
            world.blocks.put(PLACED.below(), Blocks.DIRT.defaultBlockState());
            world.blocks.put(PLACED.above(), Blocks.BAMBOO.defaultBlockState().setValue(BambooStalkBlock.AGE, age));
            BlockState state = placedState(world);
            assertTrue(state.is(Blocks.BAMBOO));
            assertEquals(Integer.valueOf(age), state.getValue(BambooStalkBlock.AGE));
        }
    }

    @Test
    public void invalidSupportRejectsWithoutNativeFallback() {
        World world = new World();
        world.blocks.put(PLACED.below(), Blocks.STONE.defaultBlockState());
        assertRejected(world);
    }

    @Test
    public void fluidRejectsWithoutNativeFallback() {
        for (BlockState fluid : new BlockState[]{Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
            World world = new World();
            world.blocks.put(PLACED.below(), Blocks.DIRT.defaultBlockState());
            world.blocks.put(PLACED, fluid);
            assertRejected(world);
        }
    }

    private static BlockState placedState(World world) {
        PlacementResult result = NmsBlockPlaceResolver.simulatePlace(world, snapshot());
        assertTrue(result.getResyncReason(), result.isSuccess());
        assertTrue(result.isConsumeInventory());
        assertEquals(PLACED, result.getPrimaryPlacedPosition());
        return result.getChangedBlocks().stream().filter(change -> change.position().equals(PLACED))
                .findFirst().orElseThrow().state();
    }

    private static void assertRejected(World world) {
        PlacementResult result = NmsBlockPlaceResolver.simulatePlace(world, snapshot());
        assertFalse(result.isSuccess());
        assertFalse(result.isConsumeInventory());
        assertTrue(result.getChangedBlocks().isEmpty());
    }

    private static PlacementSnapshot snapshot() {
        BlockPos clicked = PLACED.below();
        return PlacementSnapshot.of(InteractionHand.MAIN_HAND,
                new org.bukkit.inventory.ItemStack(Material.BAMBOO, 8),
                new net.minecraft.world.item.ItemStack(Items.BAMBOO, 8), clicked, PLACED, Direction.UP,
                new Vec3(0.5, 64, 0.5), new Vec3(0.5, 1, 0.5), false, new Vec3(0.5, 64, -3),
                0, 0, Direction.SOUTH, false, GameMode.SURVIVAL, -64, 320, false);
    }

    private static final class World implements PlacementBlockAccess {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        @Override public BlockState getBlockStateAt(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidIfLoaded(BlockPos pos) {
            return getBlockStateAt(pos).getFluidState();
        }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return true;
        }
    }
}
