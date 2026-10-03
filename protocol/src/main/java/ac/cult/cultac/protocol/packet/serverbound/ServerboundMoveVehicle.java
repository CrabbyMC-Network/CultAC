package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.Vec3d;
import java.util.Objects;

public record ServerboundMoveVehicle(Vec3d position, float yaw, float pitch, boolean onGround, boolean hasOnGround)
        implements ServerboundPacket {
    public ServerboundMoveVehicle { Objects.requireNonNull(position); }
}
