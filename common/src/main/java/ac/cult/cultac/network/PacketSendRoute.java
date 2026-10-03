package ac.cult.cultac.network;

import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;

public final class PacketSendRoute<R> {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final PacketSendRoute EMPTY = new PacketSendRoute(new PacketSendHandler[0]);

    private final PacketSendHandler<? super R>[] handlers;

    private PacketSendRoute(PacketSendHandler<? super R>[] handlers) {
        this.handlers = handlers;
    }

    public static <R> PacketSendRoute<R> of(PacketSendHandler<? super R>[] handlers) {
        if (handlers.length == 0) {
            return empty();
        }
        return new PacketSendRoute<>(handlers.clone());
    }

    @SuppressWarnings("unchecked")
    public static <R> PacketSendRoute<R> empty() { return EMPTY; }

    public boolean isEmpty() {
        return handlers.length == 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dispatch(PacketSendEvent<?> event, CultPlayer player, R packet) {
        for (PacketSendHandler<? super R> handler : handlers) {
            handler.handle((PacketSendEvent) event, player, packet);
        }
    }
}
