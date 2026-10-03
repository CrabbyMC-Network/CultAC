package ac.cult.cultac.protocol.packet.serverbound;

public record ServerboundPlayerInput(
        boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint)
        implements ServerboundPacket {}
