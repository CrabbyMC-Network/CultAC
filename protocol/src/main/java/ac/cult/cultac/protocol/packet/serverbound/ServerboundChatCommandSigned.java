package ac.cult.cultac.protocol.packet.serverbound;

public record ServerboundChatCommandSigned(String command) implements ServerboundPacket {}
