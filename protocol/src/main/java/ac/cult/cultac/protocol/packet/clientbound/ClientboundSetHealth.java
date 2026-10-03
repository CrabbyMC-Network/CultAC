package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundSetHealth(float health, int food, float saturation) implements ClientboundPacket {}
