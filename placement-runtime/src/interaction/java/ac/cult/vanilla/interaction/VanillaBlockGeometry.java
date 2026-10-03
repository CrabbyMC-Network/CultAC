package ac.cult.vanilla.interaction;

import ac.cult.placement.api.BlockGeometry;
import ac.cult.placement.api.PlacementEngine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Read-only access to the same immutable vanilla registry used for placement. */
final class VanillaBlockGeometry {
    private VanillaBlockGeometry() {}

    static BlockState state(int id) {
        // Block.stateById silently maps invalid IDs to air. Do not let invalid host
        // data turn a solid-block query into a collision-free answer.
        if (id < 0 || id >= Block.BLOCK_STATE_REGISTRY.size()) {
            throw new IllegalArgumentException("Unknown block state ID: " + id);
        }
        return Block.stateById(id);
    }

    static BlockGeometry.State describe(int id) {
        var state = state(id);
        var properties = new LinkedHashMap<String, String>();
        for (var property : state.getProperties()) {
            properties.put(property.getName(), propertyValue(state, property));
        }
        var block = state.getBlock();
        return new BlockGeometry.State(
                id,
                BuiltInRegistries.BLOCK.getKey(block).toString(),
                properties,
                state.isAir(),
                state.canBeReplaced(),
                block.getFriction(),
                block.getSpeedFactor(),
                block.getJumpFactor());
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    static List<PlacementEngine.Box> shape(
            PlacementEngine.World world,
            PlacementEngine.Pos position,
            int id,
            BlockGeometry.Shape kind,
            BlockGeometry.Context actor) {
        Objects.requireNonNull(world, "world");
        var pos = new BlockPos(position.x(), position.y(), position.z());
        var state = state(id);
        var view = getter(world);
        CollisionContext context = actor == null ? CollisionContext.empty() : new Context(actor);
        var shape = switch (kind) {
            case COLLISION -> state.getCollisionShape(view, pos, context);
            case OUTLINE -> state.getShape(view, pos, context);
            case VISUAL -> state.getVisualShape(view, pos, context);
            case INTERACTION -> state.getInteractionShape(view, pos);
            case SUPPORT -> state.getBlockSupportShape(view, pos);
            case OCCLUSION -> state.getOcclusionShape();
        };
        return shape.toAabbs().stream()
                .map(b -> new PlacementEngine.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ))
                .toList();
    }

    private static BlockGetter getter(PlacementEngine.World world) {
        return new BlockGetter() {
            public BlockState getBlockState(BlockPos pos) {
                return state(world.stateAt(pos.getX(), pos.getY(), pos.getZ()));
            }

            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            public int getMinY() {
                return world.minY();
            }

            public int getHeight() {
                return world.height();
            }
        };
    }

    /** EntityCollisionContext's vanilla comparisons, using only client-known values. */
    private static final class Context implements CollisionContext {
        private final BlockGeometry.Context actor;
        private final Item heldItem;

        private Context(BlockGeometry.Context actor) {
            this.actor = actor;
            this.heldItem = BuiltInRegistries.ITEM
                    .get(Identifier.parse(actor.heldItem()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown held item: " + actor.heldItem()))
                    .value();
        }

        public boolean isDescending() {
            return actor.descending();
        }

        public boolean isPlacement() {
            return actor.placement();
        }

        public boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue) {
            return actor.entityBottom() > pos.getY() + shape.max(Direction.Axis.Y) - 1.0E-5F;
        }

        public boolean isHoldingItem(Item item) {
            return heldItem == item;
        }

        public boolean alwaysCollideWithFluid() {
            return false;
        }

        public boolean canStandOnFluid(FluidState above, FluidState fluid) {
            return false;
        }

        public VoxelShape getCollisionShape(BlockState state, CollisionGetter world, BlockPos pos) {
            return state.getCollisionShape(world, pos, this);
        }
    }
}
