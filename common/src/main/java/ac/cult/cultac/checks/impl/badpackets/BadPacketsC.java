package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.protocol.value.PlayerCommandAction;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerCommand;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "BadPacketsC", stableKey = "cult.badpackets.wake_not_sleeping", description = "Tried to wake up while not sleeping", experimental = true)
public class BadPacketsC extends Check implements CheckListener {
    public BadPacketsC(@NotNull CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onPlayerCommand(PacketReceiveEvent<ServerboundPlayerCommand> event, CultPlayer player, ServerboundPlayerCommand packet) {

        if (packet.action() == PlayerCommandAction.STOP_SLEEPING && !player.isInBed) {
            flag();
        }
    }
}
