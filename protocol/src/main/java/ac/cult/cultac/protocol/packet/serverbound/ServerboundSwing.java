package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.value.SwingKind;

import java.util.Objects;

public record ServerboundSwing(Hand hand, SwingKind kind) implements ServerboundPacket {
    public static final ServerboundSwing PUNCH = new ServerboundSwing(Hand.MAIN_HAND, SwingKind.PUNCH);

    public ServerboundSwing {
        Objects.requireNonNull(hand);
        Objects.requireNonNull(kind);
    }
}
