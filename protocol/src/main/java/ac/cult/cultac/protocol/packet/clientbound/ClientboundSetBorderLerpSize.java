package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundSetBorderLerpSize(double oldSize, double newSize, long lerpTime)
        implements ClientboundPacket {}
