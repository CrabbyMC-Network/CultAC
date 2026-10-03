package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundSetBorderCenter(double centerX, double centerZ) implements ClientboundPacket {}
