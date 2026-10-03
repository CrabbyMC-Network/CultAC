package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.BlockPos;
import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.protocol.value.PlayerAction;
import java.util.Objects;

public record ServerboundPlayerAction(PlayerAction action, BlockPos position, Direction direction, int sequence)
        implements ServerboundPacket {
    public ServerboundPlayerAction {
        Objects.requireNonNull(action);
        Objects.requireNonNull(position);
        Objects.requireNonNull(direction);
    }
}
