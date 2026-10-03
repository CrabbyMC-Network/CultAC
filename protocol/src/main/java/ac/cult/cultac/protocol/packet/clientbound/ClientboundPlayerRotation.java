package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundPlayerRotation(float yaw, boolean relativeYaw, float pitch, boolean relativePitch)
        implements ClientboundPacket { }
