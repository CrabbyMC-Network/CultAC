package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundBlockChangedAck(int sequence) implements ClientboundPacket {}
