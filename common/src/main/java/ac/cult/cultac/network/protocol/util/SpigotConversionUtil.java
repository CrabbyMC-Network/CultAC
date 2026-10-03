package ac.cult.cultac.network.protocol.util;

import ac.cult.cultac.protocol.value.Direction;
import net.minecraft.network.HashedStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SpigotConversionUtil {

    private SpigotConversionUtil() {}

    public static net.minecraft.world.InteractionHand toNmsHand(ac.cult.cultac.protocol.value.Hand hand) {
        return switch (hand) {
            case MAIN_HAND -> net.minecraft.world.InteractionHand.MAIN_HAND;
            case OFF_HAND -> net.minecraft.world.InteractionHand.OFF_HAND;
        };
    }

    public static net.minecraft.world.phys.Vec3 toNmsVec(ac.cult.cultac.protocol.value.Vec3d vector) {
        return new net.minecraft.world.phys.Vec3(vector.x(), vector.y(), vector.z());
    }

    public static net.minecraft.core.BlockPos toNmsBlockPos(ac.cult.cultac.protocol.value.BlockPos position) {
        return new net.minecraft.core.BlockPos(position.x(), position.y(), position.z());
    }

    public static Direction toBukkitFace(ac.cult.cultac.protocol.value.Direction direction) {
        return switch (direction) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    public static net.minecraft.world.item.ItemStack toNmsItemStack(ItemStack stack) {
        return ac.cult.cultac.utils.inventory.ItemUtil.copy(stack);
    }

    public static net.minecraft.world.item.component.BundleContents.Mutable mutableBundle(
            net.minecraft.world.item.component.BundleContents contents) {
        try {
            try {
                return (net.minecraft.world.item.component.BundleContents.Mutable)
                        contents.getClass().getMethod("asMutable").invoke(contents);
            } catch (NoSuchMethodException legacy) {
                return net.minecraft.world.item.component.BundleContents.Mutable.class
                        .getConstructor(net.minecraft.world.item.component.BundleContents.class)
                        .newInstance(contents);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to copy bundle contents", failure);
        }
    }

    /**
     * Equivalent to {@code toNmsItemStack(stack).getItem()} without copying the stack and its
     * component map: empty stacks (AIR or amount <= 0) map to AIR, as {@code ItemStack.EMPTY} does.
     */
    public static net.minecraft.world.item.Item toNmsItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return net.minecraft.world.item.Items.AIR;
        }
        return stack.getItem();
    }

    /** Packet stacks remain owned by their decoder; prediction always receives a copy. */
    public static ItemStack fromNmsItemStack(net.minecraft.world.item.ItemStack stack) {
        return ac.cult.cultac.utils.inventory.ItemUtil.copy(stack);
    }

    public static ItemStack fromHashedStack(HashedStack stack) {
        if (stack == null || stack == HashedStack.EMPTY) {
            return ItemStack.EMPTY;
        }
        if (!(stack instanceof HashedStack.ActualItem actualItem)) {
            return ItemStack.EMPTY;
        }

        return fromNmsItemStack(new net.minecraft.world.item.ItemStack(actualItem.item(), actualItem.count()));
    }

    public static BlockState fromNmsBlockState(BlockState state) {
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }
}
