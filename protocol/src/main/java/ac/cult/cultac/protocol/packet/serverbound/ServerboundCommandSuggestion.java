package ac.cult.cultac.protocol.packet.serverbound;

/** The checks consume text; the original buffer retains the unused request ID. */
public record ServerboundCommandSuggestion(String command) implements ServerboundPacket {}
