package ac.cult.cultac.platform.api.player;

import net.minecraft.world.item.ItemStack;

public interface PlatformInventory {
    ItemStack getStack(int bukkitSlot, int vanillaSlot);

    ItemStack getMainHand();

    ItemStack getOffHand();
}
