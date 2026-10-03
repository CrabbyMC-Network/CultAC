package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Consumed inventory values. The platform decodes items with its vanilla ByteBuf helpers. */
public final class InventoryPackets {
    private InventoryPackets() { }

    public record CreativeSlot(int slot, ItemStack item) implements ServerboundPacket { }

    public record Content(int windowId, int stateId, List<ItemStack> items,
                          ItemStack carriedItem) implements ClientboundPacket {
        public Content { items = List.copyOf(items); }
    }

    public record Slot(int windowId, int stateId, int slot, ItemStack item) implements ClientboundPacket { }

    public record PlayerInventory(int slot, ItemStack item) implements ClientboundPacket { }

    public record Cursor(ItemStack item) implements ClientboundPacket { }

    /** Vanilla item values stay at the existing equipment consumer; only self equipment needs Bukkit conversion. */
    public record Equipment(int entityId, List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots)
            implements ClientboundPacket {
        public Equipment { slots = List.copyOf(slots); }
    }

    public record MerchantOffer(ItemStack costA, ItemStack costB, ItemStack result, boolean outOfStock) { }

    public record Offers(int windowId, List<MerchantOffer> offers) implements ClientboundPacket {
        public Offers { offers = List.copyOf(offers); }
    }
}
