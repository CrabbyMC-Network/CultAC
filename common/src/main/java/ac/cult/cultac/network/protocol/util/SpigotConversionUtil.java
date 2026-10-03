package ac.cult.cultac.network.protocol.util;

import ac.cult.cultac.utils.nmsutil.NmsBlockTags;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.network.HashedStack;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.ItemStack;

public final class SpigotConversionUtil {
    private static final Method BLOCK_STATE_TO_BLOCK_DATA = resolveBlockStateToBlockData();
    private static final Method NMS_ITEM_TO_BUKKIT_COPY = resolveNmsItemToBukkitCopy();

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

    public static org.bukkit.block.BlockFace toBukkitFace(ac.cult.cultac.protocol.value.Direction direction) {
        return switch (direction) {
            case DOWN -> org.bukkit.block.BlockFace.DOWN;
            case UP -> org.bukkit.block.BlockFace.UP;
            case NORTH -> org.bukkit.block.BlockFace.NORTH;
            case SOUTH -> org.bukkit.block.BlockFace.SOUTH;
            case WEST -> org.bukkit.block.BlockFace.WEST;
            case EAST -> org.bukkit.block.BlockFace.EAST;
        };
    }

    public static net.minecraft.world.item.ItemStack toNmsItemStack(ItemStack stack) {
        return stack == null ? net.minecraft.world.item.ItemStack.EMPTY : CraftItemStack.asNMSCopy(stack);
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
        net.minecraft.world.item.Item item = CraftMagicNumbers.getItem(stack.getType());
        return item == null ? net.minecraft.world.item.Items.AIR : item;
    }

    public static ItemStack fromNmsItemStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.empty();
        }

        try {
            return (ItemStack) NMS_ITEM_TO_BUKKIT_COPY.invoke(null, stack);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access the Paper ItemStack copy method", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Paper ItemStack copy failed", cause);
        }
    }

    private static Method resolveNmsItemToBukkitCopy() {
        // Paper 26.3 replaced asBukkitCopy(ItemStack) with asBukkitCopy(ItemInstance).
        // Resolve either signature without linking older runtimes to ItemInstance.
        // Keep copy semantics: a mirror would retain the mutable packet stack.
        for (Method method : CraftItemStack.class.getMethods()) {
            if (method.getName().equals("asBukkitCopy")
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(net.minecraft.world.item.ItemStack.class)
                    && ItemStack.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        throw new IllegalStateException("Unable to resolve Paper ItemStack to Bukkit copy method");
    }

    public static ItemStack fromHashedStack(HashedStack stack) {
        if (stack == null || stack == HashedStack.EMPTY) {
            return ItemStack.empty();
        }
        if (!(stack instanceof HashedStack.ActualItem actualItem)) {
            return ItemStack.empty();
        }

        return fromNmsItemStack(new net.minecraft.world.item.ItemStack(actualItem.item(), actualItem.count()));
    }

    public static BlockData fromBukkitBlockData(BlockData data) {
        if (data == null) {
            return Material.AIR.createBlockData();
        }
        return fromNmsBlockState(NmsBlockTags.toNmsState(data)).clone();
    }

    public static BlockData fromNmsBlockState(net.minecraft.world.level.block.state.BlockState state) {
        try {
            return (BlockData) BLOCK_STATE_TO_BLOCK_DATA.invoke(state);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access the Paper BlockState conversion method", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Paper BlockState conversion failed", cause);
        }
    }

    private static Method resolveBlockStateToBlockData() {
        Class<?> stateClass = net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class;
        for (String name : new String[] {"asBlockData", "createCraftBlockData"}) {
            try {
                Method method = stateClass.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue to the name used by the other supported Paper server generations.
            }
        }
        throw new IllegalStateException("Unable to resolve Paper BlockState to BlockData conversion method");
    }
}
