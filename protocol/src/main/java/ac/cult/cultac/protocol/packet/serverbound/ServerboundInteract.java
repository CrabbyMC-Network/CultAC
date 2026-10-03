package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.value.InteractAction;
import ac.cult.cultac.protocol.value.Vec3d;

import java.util.Objects;
import java.util.Optional;

/** Entity interactions, including attacks moved to their own wire ID in 26.1. */
public record ServerboundInteract(int entityId, InteractAction action, Hand hand,
                                  Optional<Vec3d> target, boolean sneaking) implements ServerboundPacket {
    public ServerboundInteract {
        Objects.requireNonNull(action);
        Objects.requireNonNull(hand);
        Objects.requireNonNull(target);
    }
}
