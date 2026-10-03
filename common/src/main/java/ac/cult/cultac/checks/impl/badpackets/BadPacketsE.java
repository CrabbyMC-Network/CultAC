package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.protocol.packet.Opaque;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;

@CheckData(name = "BadPacketsE", stableKey = "cult.badpackets.invalid_position", description = "Sent too many movement packets without updating position")
public class BadPacketsE extends Check implements CheckListener {
    private static final Verbose V = Verbose.of("ticks={uint}");

    private int noReminderTicks;
    private final int maxNoReminderTicks;

    public BadPacketsE(CultPlayer player) {
        super(player);
        maxNoReminderTicks = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8) ? 20 : 19;
    }

    @CultPacketHandler
    public void onMovePlayer(PacketReceiveEvent<ServerboundMovePlayer> event, CultPlayer player, ServerboundMovePlayer packet) {
        if (packet.hasPosition()) {
            noReminderTicks = 0;
            return;
        }

        if (!player.packetStateData.lastPacketWasTeleport && ++noReminderTicks > maxNoReminderTicks) {
            flag(V.write(verbose()).uint(noReminderTicks));
        }
    }

    // A mounted LocalPlayer#tick sends the passenger Rot instead of sendPosition(),
    // so its position reminder does not run. Legacy STEER_VEHICLE packets are
    // observed before ViaBackwards and call handleLegacySteerVehicle() instead.
    @CultPacketHandler("serverbound.client_tick_end")
    public void onClientTickEnd(PacketReceiveEvent<Opaque> event, CultPlayer player, Opaque packet) {
        if (player.inVehicle()) {
            noReminderTicks = 0;
        }
    }

    public void handleLegacySteerVehicle() {
        noReminderTicks = 0;
    }

    public void handleRespawn() {
        noReminderTicks = 0;
    }
}
