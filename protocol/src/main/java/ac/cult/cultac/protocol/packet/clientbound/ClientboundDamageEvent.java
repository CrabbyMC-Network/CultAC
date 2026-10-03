package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundDamageEvent(int entityId) implements ClientboundPacket { }
