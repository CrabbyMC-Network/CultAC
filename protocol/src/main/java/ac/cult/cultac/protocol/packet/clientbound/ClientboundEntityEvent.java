package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundEntityEvent(int entityId, byte status) implements ClientboundPacket {}
