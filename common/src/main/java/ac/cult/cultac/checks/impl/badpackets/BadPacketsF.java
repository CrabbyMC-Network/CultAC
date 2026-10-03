package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerCommand;
import ac.cult.cultac.protocol.value.PlayerCommandAction;
import ac.grim.grimac.api.storage.verbose.Verbose;

@CheckData(
        name = "BadPacketsF",
        stableKey = "cult.badpackets.duplicate_sprint",
        description = "Sent duplicate sprinting status")
public class BadPacketsF extends Check implements CheckListener {
    private static final Verbose V = Verbose.of("state={bool}");

    public boolean lastSprinting;
    public boolean exemptNext = true; // Support 1.14+ clients starting on either true or false sprinting, we don't know

    public BadPacketsF(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onPlayerCommand(
            PacketReceiveEvent<ServerboundPlayerCommand> event, CultPlayer player, ServerboundPlayerCommand packet) {
        if (packet.action() == PlayerCommandAction.START_SPRINTING) {
            if (lastSprinting) {
                if (exemptNext) {
                    exemptNext = false;
                    return;
                }
                boolean state = true;
                if (flag(V.write(verbose()).bool(state)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }

            lastSprinting = true;
        } else if (packet.action() == PlayerCommandAction.STOP_SPRINTING) {
            if (!lastSprinting) {
                if (exemptNext) {
                    exemptNext = false;
                    return;
                }
                boolean state = false;
                if (flag(V.write(verbose()).bool(state)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }

            lastSprinting = false;
        }
    }
}
