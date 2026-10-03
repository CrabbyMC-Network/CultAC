package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.PositionPath;
import java.util.Objects;

public record ClientboundEntityPositionSync(
        int entityId, PositionPath position, float yaw, float pitch, boolean onGround) implements ClientboundPacket {
    public ClientboundEntityPositionSync {
        Objects.requireNonNull(position);
    }
}
