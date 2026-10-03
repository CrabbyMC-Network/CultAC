package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Consumed inventory values. The platform decodes items with its vanilla ByteBuf helpers. */
public final class InventoryPackets {
    private InventoryPackets() {}

    public record CreativeSlot(int slot, ItemStack item) implements ServerboundPacket {}

    public record Content(int windowId, int stateId, List<ItemStack> items, ItemStack carriedItem)
            implements ClientboundPacket {
        public Content {
            items = List.copyOf(items);
        }
    }

    public record Slot(int windowId, int stateId, int slot, ItemStack item) implements ClientboundPacket {}

    public record PlayerInventory(int slot, ItemStack item) implements ClientboundPacket {}

    public record Cursor(ItemStack item) implements ClientboundPacket {}

    /** Equipment values remain owned by the packet; consumers copy stacks before predicting changes. */
    public record Equipment(int entityId, List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots)
            implements ClientboundPacket {
        public Equipment {
            slots = List.copyOf(slots);
        }
    }

    public record MerchantOffer(ItemStack costA, ItemStack costB, ItemStack result, boolean outOfStock) {}

    public record Offers(int windowId, List<MerchantOffer> offers) implements ClientboundPacket {
        public Offers {
            offers = List.copyOf(offers);
        }
    }
}
