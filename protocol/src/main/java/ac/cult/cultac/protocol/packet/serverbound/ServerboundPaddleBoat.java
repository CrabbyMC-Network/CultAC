package ac.cult.cultac.protocol.packet.serverbound;

public record ServerboundPaddleBoat(boolean left, boolean right) implements ServerboundPacket {}
