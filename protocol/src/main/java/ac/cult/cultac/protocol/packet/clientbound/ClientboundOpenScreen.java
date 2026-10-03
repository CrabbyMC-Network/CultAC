package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundOpenScreen(int containerId, String menuType) implements ClientboundPacket {
}
