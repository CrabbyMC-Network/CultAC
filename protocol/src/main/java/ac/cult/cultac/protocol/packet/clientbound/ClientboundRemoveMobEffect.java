package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundRemoveMobEffect(int entityId, String effect) implements ClientboundPacket {
    public ClientboundRemoveMobEffect {
        java.util.Objects.requireNonNull(effect);
    }
}
