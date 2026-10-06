package ac.cult.cultac.utils.nmsutil;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.network.protocol.util.SpigotConversionUtil;
import ac.cult.cultac.network.protocol.util.FoliaCompatUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class IsUsingItem {
    /** Servers older than 1.21.11 have no UseEffects item component. */
    private static final DataComponentType<UseEffects> USE_EFFECTS_COMPONENT = useEffectsComponent();

    public static boolean isUsingItem(CultPlayer player) {
        if (player.bukkitPlayer == null) return false;

        ItemStack activeItem = player.bukkitPlayer.getActiveItem();
        return activeItem != null && !activeItem.getType().isAir();
    }

    public static boolean isSlowDueToUsingItem(CultPlayer player) {
        ItemStack activeItem = getActiveItem(player);
        if (activeItem == null || activeItem.getType().isAir()) {
            return false;
        }
        if (USE_EFFECTS_COMPONENT != null) {
            return !getUseEffects(activeItem).canSprint();
        }
        // Before the UseEffects component existed, the client slowed while using
        // items with eat, drink, or block animations.
        ItemUseAnimation animation = SpigotConversionUtil.toNmsItemStack(activeItem).getUseAnimation();
        return animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BLOCK;
    }

    public static float getUseItemSpeedMultiplier(CultPlayer player) {
        ItemStack activeItem = getActiveItem(player);
        if (activeItem == null || activeItem.getType().isAir()) {
            return 1.0F;
        }
        if (USE_EFFECTS_COMPONENT != null) {
            return getUseEffects(activeItem).speedMultiplier();
        }
        ItemUseAnimation animation = SpigotConversionUtil.toNmsItemStack(activeItem).getUseAnimation();
        return animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BLOCK ? 0.2F : 1.0F;
    }

    public static void stopUseItem(CultPlayer player) {
        if (player.bukkitPlayer == null) return;
        stopUseItem(player.bukkitPlayer, CultAPI.INSTANCE.getPlugin());
    }

    static void stopUseItem(Player player, Plugin plugin) {
        // clearActiveItem calls LivingEntity.stopUsingItem, which emits an
        // ITEM_INTERACT_FINISH game event. Its listeners may access the world,
        // so both packet callbacks and asynchronous enforcement must hand this
        // mutation to the player's owning region. A retired entity is a no-op.
        if (player == null || plugin == null) return;
        FoliaCompatUtil.runTaskForEntity(player, plugin, player::clearActiveItem, null, 0);
    }

    private static ItemStack getActiveItem(CultPlayer player) {
        if (player.bukkitPlayer == null) return null;
        return player.bukkitPlayer.getActiveItem();
    }

    private static UseEffects getUseEffects(ItemStack item) {
        return SpigotConversionUtil.toNmsItemStack(item).getOrDefault(USE_EFFECTS_COMPONENT, UseEffects.DEFAULT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataComponentType<UseEffects> useEffectsComponent() {
        try {
            return (DataComponentType) DataComponents.class.getField("USE_EFFECTS").get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
