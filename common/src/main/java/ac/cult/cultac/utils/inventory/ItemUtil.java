package ac.cult.cultac.utils.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Item operations shared by the compensated inventory and platform adapters. */
public final class ItemUtil {
    private static final Class<?> LEGACY_SWORD_CLASS = legacySwordClass();

    private static Class<?> legacySwordClass() {
        try {
            Tool.class.getMethod("canDestroyBlocksInCreative");
            return null;
        } catch (NoSuchMethodException legacy) {
            try {
                return Class.forName("net.minecraft.world.item.SwordItem");
            } catch (ClassNotFoundException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private ItemUtil() {}

    public static ItemStack empty() {
        return ItemStack.EMPTY;
    }

    public static ItemStack of(Item item, int amount) {
        return item == null || item == Items.AIR || amount <= 0 ? ItemStack.EMPTY : new ItemStack(item, amount);
    }

    public static ItemStack copy(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static ItemStack split(ItemStack stack, int amount) {
        return stack == null || stack.isEmpty() || amount <= 0 ? ItemStack.EMPTY : stack.split(amount);
    }

    public static void grow(ItemStack stack, int amount) {
        if (stack != null && amount > 0) stack.setCount(Math.max(0, stack.getCount() + amount));
    }

    public static boolean isSameItemSameTags(ItemStack first, ItemStack second) {
        if (first == null || first.isEmpty()) return second == null || second.isEmpty();
        return second != null && !second.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
    }

    public static boolean isDamaged(ItemStack stack) {
        return getDamageValue(stack) > 0;
    }

    public static int getDamageValue(ItemStack stack) {
        return stack == null ? 0 : stack.getOrDefault(DataComponents.DAMAGE, 0);
    }

    public static int getMaxDamage(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.getItem().components().getOrDefault(DataComponents.MAX_DAMAGE, 0));
    }

    public static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) return 0;
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .entrySet()) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public static String name(Item item) {
        return ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil.registryPath(BuiltInRegistries.ITEM, item)
                .toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isSword(Item item) {
        if (item == null) return false;
        if (LEGACY_SWORD_CLASS != null) return LEGACY_SWORD_CLASS.isInstance(item);
        Tool tool = item.components().get(DataComponents.TOOL);
        return tool != null && !tool.canDestroyBlocksInCreative();
    }
}
