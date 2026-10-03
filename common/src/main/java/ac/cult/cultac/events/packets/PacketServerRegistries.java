package ac.cult.cultac.events.packets;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.Opaque;

/** Tracks the tags a client received; PLAY updates apply at their transaction boundary. */
public class PacketServerRegistries {
    @CultPacketHandler
    public void onTags(
            PacketSendEvent<ac.cult.cultac.network.packet.RegistryTags> event,
            CultPlayer player,
            ac.cult.cultac.network.packet.RegistryTags packet) {
        if (player.registryState == null)
            player.registryState = new ac.cult.cultac.utils.latency.ClientComponentRegistries();
        var state = player.registryState;
        if (event.getPhase() == ac.cult.cultac.protocol.ConnectionPhase.CONFIGURATION) state.appendTags(packet);
        else {
            player.sendTransaction();
            player.latencyUtils.addRealTimeTaskNext(() -> state.appendTags(packet));
            event.getTasksAfterSend().add(player::sendTransaction);
        }
    }

    @CultPacketHandler("clientbound.start_configuration")
    public void onStartConfiguration(PacketSendEvent<Opaque> event, CultPlayer player, Opaque packet) {
        player.registryState = null;
    }
}
