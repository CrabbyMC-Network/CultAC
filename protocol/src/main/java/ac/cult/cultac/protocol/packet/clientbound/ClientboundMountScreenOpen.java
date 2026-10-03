package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundMountScreenOpen(int containerId, int inventoryColumns, int entityId)
        implements ClientboundPacket {}
