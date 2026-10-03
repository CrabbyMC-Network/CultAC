package ac.cult.cultac.network;

import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;

@FunctionalInterface
public interface PacketReceiveHandler<T> {
    void handle(PacketReceiveEvent event, CultPlayer player, T packet);
}
