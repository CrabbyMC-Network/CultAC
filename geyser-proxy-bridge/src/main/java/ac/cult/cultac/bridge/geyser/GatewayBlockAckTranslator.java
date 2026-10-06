package ac.cult.cultac.bridge.geyser;

import java.util.function.Predicate;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;

/** Native FIFO markers bracket stock endPredictionsUpTo's client-only corrections. */
final class GatewayBlockAckTranslator extends PacketTranslator<ClientboundBlockChangedAckPacket> implements AutoCloseable {
    private final PacketTranslator<ClientboundBlockChangedAckPacket> delegate;
    private final Predicate<GeyserSession> active;
    @SuppressWarnings("unchecked") GatewayBlockAckTranslator(Predicate<GeyserSession> active) {
        this.active = active;
        delegate = (PacketTranslator<ClientboundBlockChangedAckPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundBlockChangedAckPacket.class);
        if (delegate == null) throw new IllegalStateException("Missing native block acknowledgement translator");
        Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundBlockChangedAckPacket.class, this);
    }
    @Override public void translate(GeyserSession session, ClientboundBlockChangedAckPacket packet) {
        if (!active.test(session)) { delegate.translate(session, packet); return; }
        session.sendUpstreamPacket(new Boundary(true));
        try { delegate.translate(session, packet); }
        finally { session.sendUpstreamPacket(new Boundary(false)); }
    }
    @Override public void close() {
        if (Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundBlockChangedAckPacket.class) == this)
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundBlockChangedAckPacket.class, delegate);
    }
    static final class Boundary extends NetworkStackLatencyPacket {
        final boolean start;
        Boundary(boolean start) { this.start = start; }
    }
}
