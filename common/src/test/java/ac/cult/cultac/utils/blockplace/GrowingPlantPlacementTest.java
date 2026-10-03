package ac.cult.cultac.utils.blockplace;

import static org.junit.Assert.*;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.cult.placement.PlacementRuntime;
import ac.cult.placement.api.InteractionEngine;
import ac.cult.placement.api.PlacementEngine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.BeforeClass;
import org.junit.Test;

public final class GrowingPlantPlacementTest {
    private static final BlockPos PLACED = new BlockPos(0, 64, 0);

    private static PlacementRuntime runtime;

    @org.junit.AfterClass
    public static void close() throws Exception {
        runtime.close();
    }

    @BeforeClass
    public static void bootstrap() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        runtime = PlacementRuntime.openVanilla(Path.of(System.getProperty("placementRuntimeJar")));
    }

    @Test
    public void bothVinesPredictSupportedPlacementAndInventoryConsumption() {
        for (boolean upward : new boolean[] {false, true}) {
            var access = new World();
            access.blocks.put(upward ? PLACED.below() : PLACED.above(), Blocks.STONE.defaultBlockState());
            var result = runtime.interact(request(access, upward));
            assertTrue(result.consumes());
            assertEquals(7, result.inventory().get(0).count());
            assertTrue(result.writes().stream().anyMatch(b -> b.pos().equals(new PlacementEngine.Pos(0, 64, 0))));
            var head = Block.stateById(result.writes().stream()
                    .filter(b -> b.pos().equals(new PlacementEngine.Pos(0, 64, 0)))
                    .findFirst()
                    .orElseThrow()
                    .state());
            assertSame(upward ? Blocks.TWISTING_VINES : Blocks.WEEPING_VINES, head.getBlock());
            assertTrue(head.getValue(GrowingPlantHeadBlock.AGE) >= 0);
            assertTrue(head.getValue(GrowingPlantHeadBlock.AGE) < 25);
        }
    }

    @Test
    public void unsupportedVinesStillRejectPlacement() {
        for (boolean upward : new boolean[] {false, true}) {
            var result = runtime.interact(request(new World(), upward));
            assertFalse(result.consumes());
            assertTrue(result.writes().isEmpty());
            assertTrue(result.inventory().isEmpty());
        }
    }

    @Test
    public void extendingVinesConvertsPreviousHeadToBody() {
        for (boolean upward : new boolean[] {false, true}) {
            var access = new World();
            Block head = upward ? Blocks.TWISTING_VINES : Blocks.WEEPING_VINES;
            Block body = upward ? Blocks.TWISTING_VINES_PLANT : Blocks.WEEPING_VINES_PLANT;
            BlockPos previous = upward ? PLACED.below() : PLACED.above();
            access.blocks.put(previous, head.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 12));
            access.blocks.put(upward ? previous.below() : previous.above(), Blocks.STONE.defaultBlockState());
            var result = runtime.interact(request(access, upward));
            assertTrue(result.consumes());
            assertTrue(result.writes().stream()
                    .anyMatch(b ->
                            b.pos().equals(new PlacementEngine.Pos(previous.getX(), previous.getY(), previous.getZ()))
                                    && Block.stateById(b.state()).is(body)));
            assertTrue(result.writes().stream()
                    .anyMatch(b -> b.pos().equals(new PlacementEngine.Pos(0, 64, 0))
                            && Block.stateById(b.state()).is(head)));
        }
    }

    @Test
    public void randomInitialAgesHaveIdenticalVineGeometryAndClimbability() {
        var access = new World();
        net.minecraft.world.level.BlockGetter level = new net.minecraft.world.level.BlockGetter() {
            public BlockState getBlockState(BlockPos pos) {
                return access.getBlockStateAt(pos);
            }

            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            public FluidState getFluidIfLoaded(BlockPos pos) {
                return getFluidState(pos);
            }

            public BlockState getBlockStateIfLoaded(BlockPos pos) {
                return getBlockState(pos);
            }

            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            public int getMinY() {
                return -64;
            }

            public int getHeight() {
                return 384;
            }
        };
        for (Block block : new Block[] {Blocks.WEEPING_VINES, Blocks.TWISTING_VINES}) {
            var base = block.defaultBlockState();
            for (int age = 0; age < 25; age++) {
                var state = base.setValue(GrowingPlantHeadBlock.AGE, age);
                assertSame(base.getShape(level, PLACED), state.getShape(level, PLACED));
                assertSame(base.getCollisionShape(level, PLACED), state.getCollisionShape(level, PLACED));
                assertTrue(state.is(BlockTags.CLIMBABLE));
            }
        }
    }

    private static InteractionEngine.Request request(World world, boolean upward) {
        var clicked = new PlacementEngine.Pos(0, upward ? 63 : 65, 0);
        var inventory = new ArrayList<>(java.util.Collections.nCopies(43, InteractionEngine.Stack.EMPTY));
        inventory.set(
                0, InteractionEngine.Stack.vanilla(upward ? "minecraft:twisting_vines" : "minecraft:weeping_vines", 8));
        var actor = new InteractionEngine.Actor(
                .5, 64, -3, 0, 0, "STANDING", "SURVIVAL", false, 20, false, 0, inventory, false, 1, 4.5);
        return new InteractionEngine.Request(
                InteractionEngine.Operation.USE_ON,
                world,
                actor,
                "MAIN_HAND",
                clicked,
                upward ? "UP" : "DOWN",
                .5,
                64 + (upward ? 0 : 1),
                .5,
                false,
                "minecraft:overworld",
                null);
    }

    private static final class World implements PlacementEngine.World {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        public BlockState getBlockStateAt(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        public int stateAt(int x, int y, int z) {
            return Block.getId(getBlockStateAt(new BlockPos(x, y, z)));
        }

        public int minY() {
            return -64;
        }

        public int height() {
            return 384;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return true;
        }
    }
}
