package ac.cult.cultac.utils.collisions;

import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.collisions.datatypes.CollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.NoCollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.SimpleCollisionBox;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class VersionedJavaBlockShapes {
    private static final List<ShapeOverride> MOVEMENT_OVERRIDES = List.of(
            movement(
                    state -> ac.cult.cultac.utils.nmsutil.NmsBlockTags.name(state.getBlock())
                            .equals("PALE_MOSS_CARPET"),
                    VersionedJavaBlockShapes::paleMossCarpetMovement),
            movement(Blocks.PITCHER_CROP, VersionedJavaBlockShapes::pitcherCropMovement));

    private static final List<ShapeOverride> VISUAL_OVERRIDES = List.of();

    private VersionedJavaBlockShapes() {}

    static Optional<CollisionBox> movement(CultPlayer player, BlockState state, int x, int y, int z) {
        Optional<CollisionBox> legacy = LegacyJavaBlockShapes.movement(player, state, x, y, z);
        if (legacy.isPresent()) return legacy;
        if (!canUseVersionedJavaShape(player, state)) {
            return Optional.empty();
        }
        return movement(player.getClientVersion(), state, x, y, z);
    }

    static Optional<CollisionBox> visual(CultPlayer player, BlockState state, int x, int y, int z) {
        if (!canUseVersionedJavaShape(player, state)) {
            return Optional.empty();
        }
        return firstMatch(VISUAL_OVERRIDES, player.getClientVersion(), state, x, y, z);
    }

    // The same Java geometry rules serve Java clients and the sparse Bedrock catalog's baseline.
    // State remains in the server registry; only the requested shape version changes.
    static Optional<CollisionBox> movement(ClientVersion shapeVersion, BlockState state, int x, int y, int z) {
        if (state == null || shapeVersion == null || shapeVersion.isOlderThan(ClientVersion.V_1_21_2)) {
            return Optional.empty();
        }
        return firstMatch(MOVEMENT_OVERRIDES, shapeVersion, state, x, y, z);
    }

    private static boolean canUseVersionedJavaShape(CultPlayer player, BlockState state) {
        return player != null
                && player.bedrockState == null
                && state != null
                && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2)
                && player.getClientVersion().isOlderThan(ClientVersion.V_26_2);
    }

    private static Optional<CollisionBox> firstMatch(
            List<ShapeOverride> overrides, ClientVersion shapeVersion, BlockState state, int x, int y, int z) {
        for (ShapeOverride override : overrides) {
            if (override.matches(state)) {
                return override.create(shapeVersion, state, x, y, z);
            }
        }
        return Optional.empty();
    }

    private static Optional<CollisionBox> paleMossCarpetMovement(
            ClientVersion shapeVersion, BlockState state, int x, int y, int z) {
        if (shapeVersion.isNewerThanOrEquals(ClientVersion.V_26_2)) {
            return Optional.empty();
        }
        if (isRaisedMossyCarpet(state)) {
            return Optional.of(NoCollisionBox.INSTANCE);
        }
        if (shapeVersion.isOlderThan(ClientVersion.V_1_21_2)) {
            return Optional.of(box(x, y, z, 0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D));
        }
        return Optional.empty();
    }

    private static Optional<CollisionBox> pitcherCropMovement(
            ClientVersion shapeVersion, BlockState state, int x, int y, int z) {
        if (!state.hasProperty(net.minecraft.world.level.block.PitcherCropBlock.AGE)
                || state.getValue(net.minecraft.world.level.block.PitcherCropBlock.AGE) != 0
                || !state.hasProperty(net.minecraft.world.level.block.PitcherCropBlock.HALF)
                || state.getValue(net.minecraft.world.level.block.PitcherCropBlock.HALF)
                        != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            return Optional.empty();
        }
        // Vanilla PitcherCropBlock#getCollisionShape checks AGE first through 1.21.4;
        // from 1.21.5 it checks HALF first, so an upper age-zero crop has no collision.
        return Optional.of(
                shapeVersion.isOlderThan(ClientVersion.V_1_21_5)
                        ? box(x, y, z, 5, -1, 5, 11, 3, 11)
                        : NoCollisionBox.INSTANCE);
    }

    private static boolean isRaisedMossyCarpet(BlockState state) {
        // Vanilla MossyCarpetBlock.BASE is the shared "bottom" property.
        var bottom = net.minecraft.world.level.block.state.properties.BlockStateProperties.BOTTOM;
        return state.hasProperty(bottom) && !state.getValue(bottom);
    }

    private static ShapeOverride movement(Block material, ShapeFactory factory) {
        return movement(state -> state.getBlock() == material, factory);
    }

    private static ShapeOverride movement(Predicate<BlockState> matcher, ShapeFactory factory) {
        return new ShapeOverride(matcher, factory);
    }

    private static SimpleCollisionBox box(
            int x, int y, int z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new SimpleCollisionBox(
                x + minX / 16.0D,
                y + minY / 16.0D,
                z + minZ / 16.0D,
                x + maxX / 16.0D,
                y + maxY / 16.0D,
                z + maxZ / 16.0D);
    }

    private record ShapeOverride(Predicate<BlockState> matcher, ShapeFactory factory) {
        boolean matches(BlockState state) {
            return matcher.test(state);
        }

        Optional<CollisionBox> create(ClientVersion shapeVersion, BlockState state, int x, int y, int z) {
            return factory.create(shapeVersion, state, x, y, z);
        }
    }

    @FunctionalInterface
    private interface ShapeFactory {
        Optional<CollisionBox> create(ClientVersion shapeVersion, BlockState state, int x, int y, int z);
    }
}
