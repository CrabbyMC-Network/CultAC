package ac.cult.cultac.protocol.packet.clientbound;

/** Effect identity and strength consumed by entity compensation. */
public record ClientboundUpdateMobEffect(int entityId, String effect, int amplifier) implements ClientboundPacket {
    public ClientboundUpdateMobEffect {
        java.util.Objects.requireNonNull(effect);
    }
}
