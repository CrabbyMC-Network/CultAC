package ac.cult.cultac.checks.impl.crash;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundClientInformation;
import ac.cult.cultac.protocol.value.ClientInformation;

@CheckData(name = "CrashE", stableKey = "cult.crash.low_view_distance", description = "Sent a client view distance below the minimum allowed value")
public class CrashE extends Check implements CheckListener {
    private static final Verbose V = Verbose.of("distance={sint}");

    public CrashE(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onClientInformation(PacketReceiveEvent<ServerboundClientInformation> event, CultPlayer player, ServerboundClientInformation packet) {
        sanitizeClientInformation(event, packet);
    }

    /** Sanitizes the play-state client information handled by this check. */
    public void sanitizeClientInformation(final PacketReceiveEvent<ServerboundClientInformation> event, ServerboundClientInformation packet) {
        ClientInformation information = packet.information();
        int viewDistance = information.viewDistance();
        if (viewDistance < 2) {
            flag(V.write(verbose()).sint(viewDistance));
            // Immutable packets must be replaced before re-encoding.
            ClientInformation fixed = new ClientInformation(
                    information.language(), 2, information.chatVisibility(), information.chatColors(),
                    information.modelCustomisation(), information.mainHand(), information.textFilteringEnabled(),
                    information.allowsListing(), information.particleStatus());
            event.replace(new ServerboundClientInformation(fixed));
        }
    }
}
