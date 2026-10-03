package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.EntityDelta;
import java.util.Objects;

public record ClientboundMoveEntity(
        int entityId,
        EntityDelta delta,
        float yaw,
        float pitch,
        boolean onGround,
        boolean hasPosition,
        boolean hasRotation)
        implements ClientboundPacket {
    public ClientboundMoveEntity {
        Objects.requireNonNull(delta);
    }
}
