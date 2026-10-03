package ac.cult.cultac.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.packet.ServerboundPackets;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PacketReceivePipelineTest {
    @Test
    public void earlyCancellationFinishesEarlyPhaseAndBlocksEveryLaterPhase() {
        List<String> calls = new ArrayList<>();
        PacketReceiveEvent<ServerboundMovePlayer> event = event();

        new PacketDispatcher.ReceiveRoute<>(
                        route(
                                (receiveEvent, player, packet) -> {
                                    calls.add("early-cancel");
                                    receiveEvent.setCancelled(true);
                                },
                                (receiveEvent, player, packet) -> calls.add("early-after-cancel")),
                        route((receiveEvent, player, packet) -> calls.add("ordinary")),
                        route((receiveEvent, player, packet) -> calls.add("tap")))
                .dispatch(event, null);

        assertEquals(List.of("early-cancel", "early-after-cancel"), calls);
        assertTrue(event.isCancelled());
    }

    @Test
    public void ordinaryCancellationStillFinishesOrdinaryPhaseAndRunsTap() {
        List<String> calls = new ArrayList<>();
        PacketReceiveEvent<ServerboundMovePlayer> event = event();

        new PacketDispatcher.ReceiveRoute<>(
                        route((receiveEvent, player, packet) -> calls.add("early")),
                        route(
                                (receiveEvent, player, packet) -> {
                                    calls.add("ordinary-cancel");
                                    receiveEvent.setCancelled(true);
                                },
                                (receiveEvent, player, packet) -> calls.add("ordinary-after-cancel")),
                        route((receiveEvent, player, packet) -> calls.add("tap")))
                .dispatch(event, null);

        assertEquals(List.of("early", "ordinary-cancel", "ordinary-after-cancel", "tap"), calls);
        assertTrue(event.isCancelled());
    }

    @Test
    public void uncancelledPacketTraversesAllPhases() {
        List<String> calls = new ArrayList<>();
        PacketReceiveEvent<ServerboundMovePlayer> event = event();

        new PacketDispatcher.ReceiveRoute<>(
                        route((receiveEvent, player, packet) -> calls.add("early")),
                        route((receiveEvent, player, packet) -> calls.add("ordinary")),
                        route((receiveEvent, player, packet) -> calls.add("tap")))
                .dispatch(event, null);

        assertEquals(List.of("early", "ordinary", "tap"), calls);
        assertFalse(event.isCancelled());
    }

    @Test
    public void replacementsStayInEventWhileEachOuterRouteKeepsOriginalArgument() {
        PacketReceiveEvent<ServerboundMovePlayer> event = event();
        ServerboundMovePlayer original = event.getPacket();
        ServerboundMovePlayer earlyReplacement = move(8);
        ServerboundMovePlayer ordinaryReplacement = move(9);
        List<ServerboundMovePlayer> arguments = new ArrayList<>();

        new PacketDispatcher.ReceiveRoute<>(
                        route(
                                (received, player, packet) -> {
                                    arguments.add(packet);
                                    received.replace(earlyReplacement);
                                },
                                (received, player, packet) -> {
                                    arguments.add(packet);
                                    assertSame(earlyReplacement, received.getPacket());
                                }),
                        route(
                                (received, player, packet) -> {
                                    arguments.add(packet);
                                    assertSame(earlyReplacement, received.getPacket());
                                    received.replace(ordinaryReplacement);
                                },
                                (received, player, packet) -> {
                                    arguments.add(packet);
                                    assertSame(ordinaryReplacement, received.getPacket());
                                }),
                        route((received, player, packet) -> arguments.add(packet)))
                .dispatch(event, null);

        assertEquals(5, arguments.size());
        for (int i = 0; i < 4; i++) assertSame(original, arguments.get(i));
        assertSame(ordinaryReplacement, arguments.get(4));
        assertSame(ordinaryReplacement, event.getPacket());
    }

    @Test
    public void aLaterHandlerCanRestoreCancellationBeforeThePhaseBoundary() {
        PacketReceiveEvent<ServerboundMovePlayer> event = event();
        List<String> calls = new ArrayList<>();
        new PacketDispatcher.ReceiveRoute<>(
                        route(
                                (received, player, packet) -> {
                                    received.setCancelled(true);
                                    calls.add("cancel");
                                },
                                (received, player, packet) -> {
                                    assertTrue(received.isCancelled());
                                    received.setCancelled(false);
                                    calls.add("restore");
                                }),
                        route((received, player, packet) -> calls.add("ordinary")),
                        route((received, player, packet) -> calls.add("tap")))
                .dispatch(event, null);
        assertEquals(List.of("cancel", "restore", "ordinary", "tap"), calls);
        assertFalse(event.isCancelled());
    }

    @SafeVarargs
    private static PacketReceiveRoute<ServerboundMovePlayer> route(
            PacketReceiveHandler<ServerboundMovePlayer>... handlers) {
        return PacketReceiveRoute.of(handlers);
    }

    private static PacketReceiveEvent<ServerboundMovePlayer> event() {
        return new PacketReceiveEvent<>(null, ConnectionPhase.PLAY, ServerboundPackets.MOVE_PLAYER, move(7));
    }

    private static ServerboundMovePlayer move(int x) {
        return new ServerboundMovePlayer(x, 0, 0, 0, 0, false, false, true, false);
    }
}
