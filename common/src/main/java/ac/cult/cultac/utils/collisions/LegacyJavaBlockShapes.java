package ac.cult.cultac.utils.collisions;

import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.utils.collisions.datatypes.CollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.SimpleCollisionBox;
import java.util.Optional;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Pre-flattening shapes from Entity/Block sources, selected after Via replacement. */
final class LegacyJavaBlockShapes {
    private LegacyJavaBlockShapes() {}

    static Optional<CollisionBox> movement(CultPlayer player, BlockState state, int x, int y, int z) {
        if (player == null
                || state == null
                || player.isBedrockMovement()
                || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            return Optional.empty();
        }
        Block material = state.getBlock();
        CollisionBox shape;
        if (material == Blocks.FARMLAND) {
            shape = box(0, 0, 0, 1, 1, 1);
        } else if (material == Blocks.LADDER) {
            shape = ladder(ac.cult.cultac.utils.nmsutil.NmsBlockTags.getFacing(state));
        } else if (material == Blocks.LILY_PAD) {
            shape = box(0, 0, 0, 1, 1.0 / 64, 1);
        } else if (material == Blocks.SNOW) {
            shape = box(
                    0, 0, 0, 1, (state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) - 1) / 8.0, 1);
        } else if (material == Blocks.ANVIL || material == Blocks.CHIPPED_ANVIL || material == Blocks.DAMAGED_ANVIL) {
            Direction face = ac.cult.cultac.utils.nmsutil.NmsBlockTags.getFacing(state);
            shape = face == Direction.EAST || face == Direction.WEST
                    ? box(0, 0, 0.125, 1, 1, 0.875)
                    : box(0.125, 0, 0, 0.875, 1, 1);
        } else {
            shape = material == Blocks.IRON_BARS
                            || material == Blocks.GLASS_PANE
                            || ac.cult.cultac.utils.nmsutil.NmsBlockTags.name(material)
                                    .endsWith("_STAINED_GLASS_PANE")
                    ? pane(player, x, y, z)
                    : null;
        }
        return shape == null ? Optional.empty() : Optional.of(shape.offset(x, y, z));
    }

    private static CollisionBox ladder(Direction facing) {
        return switch (facing) {
            case NORTH -> box(0, 0, 0.875, 1, 1, 1);
            case SOUTH -> box(0, 0, 0, 1, 1, 0.125);
            case WEST -> box(0.875, 0, 0, 1, 1, 1);
            default -> box(0, 0, 0, 0.125, 1, 1);
        };
    }

    private static CollisionBox pane(CultPlayer player, int x, int y, int z) {
        // BlockPane#addCollisionBoxesToList makes an isolated 1.8 pane a cross.
        boolean n = connects(player, x, y, z - 1);
        boolean s = connects(player, x, y, z + 1);
        boolean w = connects(player, x - 1, y, z);
        boolean e = connects(player, x + 1, y, z);
        boolean isolated = !n && !s && !w && !e;
        ComplexCollisionBox shape = new ComplexCollisionBox();
        if (w || e || isolated) shape.add(box(w || isolated ? 0 : 0.5, 0, 0.4375, e || isolated ? 1 : 0.5, 1, 0.5625));
        if (n || s || isolated) shape.add(box(0.4375, 0, n || isolated ? 0 : 0.5, 0.5625, 1, s || isolated ? 1 : 0.5));
        return shape;
    }

    private static boolean connects(CultPlayer player, int x, int y, int z) {
        var state = player.compensatedWorld.getBlockStateAt(x, y, z);
        Block material = state.getBlock();
        // 1.8 Block#isFullBlock caches isOpaqueCube during construction. Leaves
        // cache true before fancy graphics is enabled; modern leaves never occlude.
        return state.getBlock() instanceof LeavesBlock
                || state.isSolidRender()
                || material == Blocks.GLASS
                || ac.cult.cultac.utils.nmsutil.NmsBlockTags.name(material).endsWith("_STAINED_GLASS")
                || material == Blocks.IRON_BARS
                || material == Blocks.GLASS_PANE
                || ac.cult.cultac.utils.nmsutil.NmsBlockTags.name(material).endsWith("_STAINED_GLASS_PANE");
    }

    private static SimpleCollisionBox box(double x, double y, double z, double xx, double yy, double zz) {
        return new SimpleCollisionBox(x, y, z, xx, yy, zz);
    }
}
