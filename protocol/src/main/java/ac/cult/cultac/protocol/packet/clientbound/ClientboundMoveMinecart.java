package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.Vec3d;
import java.util.List;
import java.util.Objects;

public record ClientboundMoveMinecart(int entityId, List<Step> steps) implements ClientboundPacket {
    public ClientboundMoveMinecart { steps = List.copyOf(steps); }

    public record Step(Vec3d position, Vec3d movement, float yaw, float pitch, float weight) {
        public Step {
            Objects.requireNonNull(position);
            Objects.requireNonNull(movement);
        }
    }
}
