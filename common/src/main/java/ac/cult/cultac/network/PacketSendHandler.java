package ac.cult.cultac.network;

import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;

@FunctionalInterface
public interface PacketSendHandler<T> {
    void handle(PacketSendEvent<T> event, CultPlayer player, T packet);
}
