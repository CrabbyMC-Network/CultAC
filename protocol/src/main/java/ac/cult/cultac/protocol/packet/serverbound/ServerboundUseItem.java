package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.Hand;
import java.util.Objects;

public record ServerboundUseItem(Hand hand, int sequence, float yaw, float pitch) implements ServerboundPacket {
    public ServerboundUseItem {
        Objects.requireNonNull(hand);
    }
}
