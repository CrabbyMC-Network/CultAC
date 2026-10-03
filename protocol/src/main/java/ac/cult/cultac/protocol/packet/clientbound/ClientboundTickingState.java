package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundTickingState(float tickRate, boolean frozen) implements ClientboundPacket {}
