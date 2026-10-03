package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.Relative;
import ac.cult.cultac.protocol.value.Vec3d;
import java.util.Objects;
import java.util.Set;

public record ClientboundTeleportEntity(int entityId, Vec3d position, Vec3d delta,
                                       float yaw, float pitch, Set<Relative> relatives,
                                       boolean onGround) implements ClientboundPacket {
    public ClientboundTeleportEntity {
        Objects.requireNonNull(position);
        Objects.requireNonNull(delta);
        relatives = Relative.copyOf(relatives);
    }
}
