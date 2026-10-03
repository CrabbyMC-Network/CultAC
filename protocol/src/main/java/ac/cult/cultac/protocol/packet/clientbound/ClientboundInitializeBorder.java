package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundInitializeBorder(
        double centerX, double centerZ, double oldSize, double newSize, long lerpTime, int absoluteMaxSize)
        implements ClientboundPacket {}
