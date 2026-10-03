package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.network.packet.RegistryData;
import ac.cult.cultac.network.packet.RegistryTags;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.packet.Opaque;
import ac.cult.cultac.vanilla.VanillaRegistryState;

/**
 * Configuration precedes PLAY decoding; in-game tag updates follow the normal transaction boundary.
 */
final class VelocityRegistryPackets {
    private static VanillaRegistryState state(PacketSendEvent<?> event) {
        return ((VelocityConnectionAdapter) event.getUser().getCultConnection().platform()).state();
    }

    @CultPacketHandler
    public void registry(PacketSendEvent<RegistryData> event, CultPlayer player, RegistryData packet) {
        state(event).append(packet.registry(), packet.entries());
    }

    @CultPacketHandler
    public void tags(PacketSendEvent<RegistryTags> event, CultPlayer player, RegistryTags packet) {
        var state = state(event);
        if (event.getPhase() == ConnectionPhase.CONFIGURATION) {
            state.appendTags(packet.tags());
        } else {
            Runnable apply = state.preparePlayTags(packet.tags());
            player.sendTransaction();
            player.latencyUtils.addRealTimeTaskNext(apply);
            event.getTasksAfterSend().add(player::sendTransaction);
        }
    }

    @CultPacketHandler("clientbound.finish_configuration")
    public void finish(PacketSendEvent<Opaque> event, CultPlayer player, Opaque packet) {
        state(event).finish();
    }
}
