package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundAnimate(int entityId, int action) implements ClientboundPacket {}
