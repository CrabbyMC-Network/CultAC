package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.Vec3d;
import java.util.Objects;

public record ClientboundMoveVehicle(Vec3d position, float yaw, float pitch) implements ClientboundPacket {
    public ClientboundMoveVehicle { Objects.requireNonNull(position); }
}
