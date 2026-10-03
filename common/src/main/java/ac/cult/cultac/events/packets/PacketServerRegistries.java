package ac.cult.cultac.events.packets;

import ac.cult.cultac.protocol.packet.Opaque;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.packet.RegistryData;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;

/** Collects component definitions during client configuration; tags use server bindings. */
public class PacketServerRegistries {
    @CultPacketHandler
    public void onRegistryData(PacketSendEvent<RegistryData> event, CultPlayer player, RegistryData packet) {
        if (player.isBedrockMovement()
                || player.getClientVersion().isOlderThan(ac.cult.cultac.network.protocol.ClientVersion.V_26_3)) return;
        if (player.registryState == null) player.registryState = new ac.cult.cultac.utils.latency.ClientComponentRegistries();
        player.registryState.append(packet);
    }

    @CultPacketHandler("clientbound.start_configuration")
    public void onStartConfiguration(PacketSendEvent<Opaque> event, CultPlayer player, Opaque packet) {
        player.registryState = null;
    }

    @CultPacketHandler("clientbound.finish_configuration")
    public void onFinishConfiguration(PacketSendEvent<Opaque> event, CultPlayer player, Opaque packet) {
        if (player.registryState != null) player.registryState.finish();
    }

}
