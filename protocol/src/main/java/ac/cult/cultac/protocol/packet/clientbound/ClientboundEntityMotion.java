package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.Vec3d;
import java.util.Objects;

public record ClientboundEntityMotion(int entityId, Vec3d velocity) implements ClientboundPacket {
    public ClientboundEntityMotion {
        Objects.requireNonNull(velocity);
    }
}
