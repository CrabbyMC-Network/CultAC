package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.ClientCommandAction;

public record ServerboundClientCommand(ClientCommandAction action) implements ServerboundPacket {}
