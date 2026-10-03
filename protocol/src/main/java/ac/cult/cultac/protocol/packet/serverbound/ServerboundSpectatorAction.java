package ac.cult.cultac.protocol.packet.serverbound;

import java.util.OptionalInt;

public record ServerboundSpectatorAction(OptionalInt target) implements ServerboundPacket {}
