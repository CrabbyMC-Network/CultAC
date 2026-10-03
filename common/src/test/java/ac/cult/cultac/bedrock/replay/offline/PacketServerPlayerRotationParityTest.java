package ac.cult.cultac.bedrock.replay.offline;

import static org.junit.Assert.*;

import ac.cult.cultac.events.packets.PacketServerPlayerRotation;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.packet.ClientboundPackets;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerRotation;
import org.junit.Test;

public final class PacketServerPlayerRotationParityTest {
    @Test
    public void standaloneRotationUsesHighLevelBundleWithoutNestedDelimiters() {
        var rotation = new ClientboundPlayerRotation(30, false, 20, false);
        var event = event(rotation, false);

        new PacketServerPlayerRotation().onPlayerRotation(event, null, rotation);

        assertTrue(event.isBundleRequested());
        assertTrue(event.getWritesBeforeSend().isEmpty());
        assertTrue(event.getWritesAfterSend().isEmpty());
        assertSame(rotation, event.getOriginalPacket());
    }

    @Test
    public void rotationAlreadyInsideBundleIsNotNested() {
        var rotation = new ClientboundPlayerRotation(30, false, 20, false);
        var event = event(rotation, true);

        new PacketServerPlayerRotation().onPlayerRotation(event, null, rotation);

        assertFalse(event.isBundleRequested());
        assertSame(rotation, event.getPacket());
    }

    @Test
    public void sanitizationPreservesRelativeFlagsAndFiniteAngles() {
        for (float yaw : new float[] {30, Float.NaN, Float.POSITIVE_INFINITY}) {
            for (float pitch : new float[] {-20, Float.NaN, Float.NEGATIVE_INFINITY}) {
                var rotation = new ClientboundPlayerRotation(yaw, true, pitch, true);
                var event = event(rotation, true);
                new PacketServerPlayerRotation().onPlayerRotation(event, null, rotation);
                assertEquals(
                        new ClientboundPlayerRotation(
                                Float.isFinite(yaw) ? yaw : 0, true, Float.isFinite(pitch) ? pitch : 0, true),
                        event.getPacket());
            }
        }
    }

    private static PacketSendEvent<ClientboundPlayerRotation> event(
            ClientboundPlayerRotation packet, boolean insideBundle) {
        return new PacketSendEvent<>(
                null, ConnectionPhase.PLAY, ClientboundPackets.PLAYER_ROTATION, packet, insideBundle);
    }
}
