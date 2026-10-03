package ac.cult.cultac.platform.bukkit.player;

import ac.cult.cultac.platform.api.player.PlatformInventory;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class BukkitPlatformInventory implements PlatformInventory {

    private final @NotNull Player bukkitPlayer;

    @Override
    public ItemStack getStack(int bukkitSlot, int vanillaSlot) {
        return CraftItemStack.asNMSCopy(bukkitPlayer.getInventory().getItem(bukkitSlot));
    }

    @Override
    public ItemStack getMainHand() {
        return CraftItemStack.asNMSCopy(bukkitPlayer.getInventory().getItemInMainHand());
    }

    @Override
    public ItemStack getOffHand() {
        return CraftItemStack.asNMSCopy(bukkitPlayer.getInventory().getItemInOffHand());
    }
}
