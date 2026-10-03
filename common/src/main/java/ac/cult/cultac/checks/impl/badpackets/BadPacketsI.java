package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAbilities;

@CheckData(
        name = "BadPacketsI",
        stableKey = "cult.badpackets.spoofed_abilities",
        description = "Claimed to be flying while unable to fly")
public class BadPacketsI extends Check implements CheckListener {
    public BadPacketsI(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onPlayerAbilities(
            PacketReceiveEvent<ServerboundPlayerAbilities> event,
            CultPlayer player,
            ServerboundPlayerAbilities packet) {
        if (packet.flying() && !player.canFly && flag() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
