package ac.cult.cultac.events.packets;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerRotation;


/** Sanitizes server-forced rotations and preserves their atomic bundle boundary. */
public class PacketServerPlayerRotation {

    @CultPacketHandler
    public void onPlayerRotation(PacketSendEvent<ClientboundPlayerRotation> event, CultPlayer player, ClientboundPlayerRotation packet) {
        float yaw = packet.yaw();
        float pitch = packet.pitch();

        if (!Float.isFinite(pitch) || !Float.isFinite(yaw)) {
            if (!Float.isFinite(pitch)) pitch = 0;
            if (!Float.isFinite(yaw)) yaw = 0;
            event.replace(new ClientboundPlayerRotation(yaw, packet.relativeYaw(), pitch, packet.relativePitch()));
        }

        if (event.isInsideBundle()) return;

        // Keep the original bytes (or the sanitized replacement) inside the
        // existing preserved group, without dispatching the child a second time.
        event.bundle();
    }
}
