package ac.cult.cultac.protocol.packet.serverbound;

public record ServerboundSelectBundleItem(int slotId, int selectedItemIndex) implements ServerboundPacket {}
