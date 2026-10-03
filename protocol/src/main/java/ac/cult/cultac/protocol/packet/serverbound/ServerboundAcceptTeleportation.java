package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.Vec3d;

/** Acknowledgements before 26.3 contain only the teleport ID. */
public record ServerboundAcceptTeleportation(int id, Vec3d position, float yaw, float pitch)
        implements ServerboundPacket {
    public boolean hasPosition() { return position != null; }
}
