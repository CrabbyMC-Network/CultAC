package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.BridgeEnvelopeCodec;
import java.util.function.Function;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

/** Native receipts on Cult backends; exactly the original translators on all other backends. */
final class GatewayTranslators implements AutoCloseable {
    private final PacketTranslator<ClientboundCustomPayloadPacket> originalPayload;
    private final PacketTranslator<ClientboundPingPacket> originalPing;
    private final PacketTranslator<ClientboundPlayerPositionPacket> originalPosition;
    private final PacketTranslator<ClientboundCustomPayloadPacket> payload;
    private final PacketTranslator<ClientboundPingPacket> ping;
    private final PacketTranslator<ClientboundPlayerPositionPacket> position;
    private final PacketTranslator<ClientboundLoginPacket> originalLogin, login;
    private final GatewayClientPoseTranslator pose;
    private final GatewayAttributeTranslator attributes;
    private final GatewayBlockAckTranslator blockAcknowledgements;

    @SuppressWarnings("unchecked") GatewayTranslators(Function<GeyserSession, GatewaySession> sessions) {
        originalPayload = (PacketTranslator<ClientboundCustomPayloadPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundCustomPayloadPacket.class);
        originalPing = (PacketTranslator<ClientboundPingPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundPingPacket.class);
        originalPosition = (PacketTranslator<ClientboundPlayerPositionPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundPlayerPositionPacket.class);
        originalLogin = (PacketTranslator<ClientboundLoginPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundLoginPacket.class);
        if (originalPayload == null || originalPing == null || originalPosition == null || originalLogin == null)
            throw new IllegalStateException("Required native Geyser translator missing");
        payload = new PacketTranslator<>() {
            @Override public void translate(GeyserSession session, ClientboundCustomPayloadPacket packet) {
                GatewaySession bridge = sessions.apply(session);
                if (bridge != null && packet.getChannel().asString().equals(BridgeEnvelopeCodec.CHANNEL)) {
                    bridge.receive(packet.getData());
                } else originalPayload.translate(session, packet);
            }
        };
        ping = new PacketTranslator<>() {
            @Override public void translate(GeyserSession session, ClientboundPingPacket packet) {
                GatewaySession bridge = sessions.apply(session);
                if (bridge == null || !bridge.ownsPing(packet.getId())) { originalPing.translate(session, packet); return; }
                // MCProtocolLib's synthetic Pong does not prove the Bedrock client processed a teleport.
                bridge.javaPing(packet.getId());
            }
        };
        position = new PacketTranslator<>() {
            @Override public void translate(GeyserSession session, ClientboundPlayerPositionPacket packet) {
                GatewaySession bridge = sessions.apply(session);
                if (bridge != null && bridge.active()) bridge.javaTeleport(packet.getId());
                originalPosition.translate(session, packet);
            }
        };
        login = new PacketTranslator<>() {
            @Override public void translate(GeyserSession session, ClientboundLoginPacket packet) {
                GatewaySession bridge = sessions.apply(session);
                if (bridge != null) bridge.javaLogin();
                originalLogin.translate(session, packet);
            }
        };
        GatewayClientPoseTranslator installedPose = null;
        GatewayAttributeTranslator installedAttributes = null;
        GatewayBlockAckTranslator installedAcknowledgements = null;
        try {
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundCustomPayloadPacket.class, payload);
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundPingPacket.class, ping);
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundPlayerPositionPacket.class, position);
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundLoginPacket.class, login);
            installedPose = new GatewayClientPoseTranslator(s -> sessions.apply(s) != null && sessions.apply(s).active());
            installedAttributes = new GatewayAttributeTranslator((s, a) -> sessions.apply(s).javaMovement(a),
                    (s, e, a) -> sessions.apply(s).javaVehicleMovement(e, a),
                    s -> sessions.apply(s) != null && sessions.apply(s).active());
            installedAcknowledgements = new GatewayBlockAckTranslator(s -> sessions.apply(s) != null && sessions.apply(s).ready());
        } catch (RuntimeException failure) {
            // A partial install would leave proxy hooks live on every backend, so roll back fully.
            if (installedAttributes != null) installedAttributes.close();
            if (installedPose != null) installedPose.close();
            restoreOriginals();
            throw failure;
        }
        pose = installedPose;
        attributes = installedAttributes;
        blockAcknowledgements = installedAcknowledgements;
    }
    @Override public void close() {
        pose.close(); attributes.close(); blockAcknowledgements.close();
        restoreOriginals();
    }
    private void restoreOriginals() {
        if (Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundCustomPayloadPacket.class) == payload)
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundCustomPayloadPacket.class, originalPayload);
        if (Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundPingPacket.class) == ping)
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundPingPacket.class, originalPing);
        if (Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundPlayerPositionPacket.class) == position)
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundPlayerPositionPacket.class, originalPosition);
        if (Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundLoginPacket.class) == login)
            Registries.JAVA_PACKET_TRANSLATORS.register(ClientboundLoginPacket.class, originalLogin);
    }
}
