package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundCooldown(String group, int duration) implements ClientboundPacket {}
