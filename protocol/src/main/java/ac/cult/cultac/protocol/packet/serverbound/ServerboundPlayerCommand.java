package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.PlayerCommandAction;

import java.util.Objects;

public record ServerboundPlayerCommand(int entityId, PlayerCommandAction action, int data) implements ServerboundPacket {
    public ServerboundPlayerCommand {
        Objects.requireNonNull(action);
    }
}
