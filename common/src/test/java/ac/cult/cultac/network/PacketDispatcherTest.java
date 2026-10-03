package ac.cult.cultac.network;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.network.event.PacketListenerPriority;
import ac.cult.cultac.protocol.*;
import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.packet.ServerboundPackets;
import org.junit.jupiter.api.Test;

class PacketDispatcherTest {
    private final ProtocolRuntime runtime = ProtocolRuntime.create(ProtocolData.load(ProtocolVersion.V26_3));
    private final PacketDispatcher dispatcher = new PacketDispatcher(runtime);

    private PacketDispatcher.Route pong() {
        int id = runtime.data()
                .packets(ConnectionPhase.PLAY, PacketDirection.SERVERBOUND)
                .id("minecraft:pong");
        return dispatcher.get(PacketDirection.SERVERBOUND, ConnectionPhase.PLAY, id);
    }

    @Test
    void batchesPublishOnlyAfterSuccessfulValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.register(batch -> {
                    batch.receive(ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p, r) -> {});
                    assertNull(pong(), "An incomplete batch must not reach packet threads");
                    batch.sendRoute(ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p, r) -> {});
                }));
        assertNull(pong());
        dispatcher.register(
                batch -> batch.receive(ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p, r) -> {}));
        var previous = pong();
        assertNotNull(previous);
        assertThrows(
                IllegalStateException.class,
                () -> dispatcher.replace(batch -> {
                    assertSame(previous, pong());
                    throw new IllegalStateException("Invalid replacement");
                }));
        assertSame(previous, pong());
    }

    @Test
    void retainedBuildersCannotMutatePublishedOrFutureRegistrations() {
        var escaped = new PacketRouteBuilder[1];
        dispatcher.register(batch -> escaped[0] = batch);
        escaped[0].receive(ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p, r) -> {});
        assertNull(pong());
        dispatcher.register(batch -> {});
        assertNull(pong());
    }

    @Test
    void nestedMutationCannotOverwriteAnOuterBatch() {
        assertThrows(
                IllegalStateException.class,
                () -> dispatcher.register(batch -> {
                    batch.receive(ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p, r) -> {});
                    dispatcher.clear();
                }));
        assertNull(pong());
    }

    @Test
    void publishedRoutesKeepPriorityAndTieOrderAcrossLaterBatches() {
        var calls = new java.util.ArrayList<String>();
        dispatcher.register(batch -> {
            batch.receiveConnection(ServerboundPackets.PONG, PacketListenerPriority.HIGH, (e, p) -> calls.add("high"));
            batch.receiveConnection(
                    ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p) -> calls.add("first"));
        });
        var previous = pong();
        dispatcher.register(batch -> {
            batch.receiveConnection(
                    ServerboundPackets.PONG, PacketListenerPriority.NORMAL, (e, p) -> calls.add("second"));
            batch.receiveConnection(ServerboundPackets.PONG, PacketListenerPriority.LOW, (e, p) -> calls.add("low"));
        });
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        var session = new CultConnection(channel, dispatcher, ignored -> null);
        var user = new ac.cult.cultac.network.protocol.player.User(
                new ac.cult.cultac.network.protocol.player.User.Profile(java.util.UUID.randomUUID(), "RouteTest"),
                session);
        var event = new ac.cult.cultac.network.event.PacketReceiveEvent<>(
                user,
                ConnectionPhase.PLAY,
                ServerboundPackets.PONG,
                new ac.cult.cultac.protocol.packet.serverbound.ServerboundPong(1));
        try {
            dispatcher.receive(event, previous.receive());
            assertEquals(java.util.List.of("first", "high"), calls);
            calls.clear();
            dispatcher.receive(event, pong().receive());
            assertEquals(java.util.List.of("low", "first", "second", "high"), calls);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
