package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.util.*;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real pinned native packet classes with a mocked Geyser event owner, never a second server. */
public class GatewaySessionTest {
    private static final class Harness {
        final UUID player = UUID.randomUUID(), connection = UUID.randomUUID();
        final BridgeEnvelopeCodec codec = new BridgeEnvelopeCodec(new byte[32]);
        final GeyserSession nativeSession = mock(GeyserSession.class, RETURNS_DEEP_STUBS);
        final GatewaySession gateway;
        final GatewayOutbound outbound;
        final List<BridgeEnvelope> backend = new ArrayList<>();
        final List<BedrockPacket> written = new ArrayList<>(), projected = new ArrayList<>();
        final Set<Long> acknowledged = new HashSet<>();
        final ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        long rootSequence = 1;
        Harness() throws Exception {
            when(nativeSession.javaUuid()).thenReturn(player);
            when(nativeSession.getDownstream().getSession().getChannel().eventLoop().inEventLoop()).thenReturn(true);
            when(nativeSession.protocolVersion()).thenReturn(975);
            when(nativeSession.getPlayerEntity().geyserId()).thenReturn(42L);
            when(nativeSession.getPlayerEntity().getEntityId()).thenReturn(7);
            when(nativeSession.getPlayerEntity().getBoundingBoxWidth()).thenReturn(.6F);
            when(nativeSession.getPlayerEntity().getBoundingBoxHeight()).thenReturn(1.8F);
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                    .when(nativeSession).ensureInEventLoop(any(Runnable.class));
            when(context.newPromise()).thenAnswer(call -> mock(ChannelPromise.class));
            when(context.write(any(), any(ChannelPromise.class))).thenAnswer(call -> {
                if (call.getArgument(0) instanceof BedrockPacketWrapper wrapper) written.add(wrapper.getPacket());
                return mock(io.netty.channel.ChannelFuture.class);
            });
            gateway = new GatewaySession(nativeSession, codec);
            Field field = GatewaySession.class.getDeclaredField("outbound"); field.setAccessible(true);
            outbound = (GatewayOutbound) field.get(gateway);
            field = GatewaySession.class.getDeclaredField("delegate"); field.setAccessible(true);
            field.set(gateway, new BedrockPacketHandler() {
                @Override public PacketSignal handlePacket(BedrockPacket packet) { projected.add(packet); return PacketSignal.HANDLED; }
            });
            doAnswer(call -> {
                if (call.getArgument(0) instanceof ServerboundCustomPayloadPacket payload) backend.add(codec.decode(payload.getData()));
                return null;
            }).when(nativeSession).sendDownstreamPacket(any(org.geysermc.mcprotocollib.network.packet.Packet.class));
            doAnswer(call -> {
                BedrockPacket packet = call.getArgument(0);
                outbound.write(context, BedrockPacketWrapper.create(0, 0, 0, packet, null), context.newPromise());
                return null;
            }).when(nativeSession).sendUpstreamPacket(any(BedrockPacket.class));
            var nativeBedrock = nativeSession.getUpstream().getSession();
            doAnswer(call -> {
                outbound.write(context, BedrockPacketWrapper.create(0, 0, 0, call.getArgument(0), null), context.newPromise());
                return null;
            }).when(nativeBedrock).sendPacketImmediately(any(BedrockPacket.class));
            var nativeTeleport = new MovePlayerPacket(); nativeTeleport.setRuntimeEntityId(42);
            nativeTeleport.setPosition(Vector3f.from(.5F, 83.62001F, .5F)); nativeTeleport.setRotation(Vector3f.ZERO);
            nativeTeleport.setMode(MovePlayerPacket.Mode.TELEPORT);
            nativeSession.sendUpstreamPacket(nativeTeleport);
        }
        void challenge() { gateway.receive(codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_GATEWAY,
                BridgeEnvelope.Kind.CHALLENGE, player, connection, 0, new byte[0]))); }
        void control(BridgeEnvelope.Kind kind, byte[] body) { gateway.receive(codec.encode(new BridgeEnvelope(
                BridgeEnvelope.Direction.TO_GATEWAY, kind, player, connection, rootSequence++, body))); }
        NetworkStackLatencyPacket lastReceipt() {
            return (NetworkStackLatencyPacket) written.stream().filter(p -> p instanceof NetworkStackLatencyPacket).reduce((a, b) -> b).orElseThrow();
        }
        long echoScale = NativeReceiptTimestamp.SCALE;
        void ack() {
            // Real Bedrock clients echo NetworkStackLatency with fromServer=false.
            var reply = new NetworkStackLatencyPacket(); reply.setFromServer(false);
            var marker = (NetworkStackLatencyPacket) written.stream().filter(p -> p instanceof NetworkStackLatencyPacket latency
                    && !acknowledged.contains(latency.getTimestamp())).findFirst().orElseThrow();
            acknowledged.add(marker.getTimestamp());
            reply.setTimestamp(Math.multiplyExact(marker.getTimestamp(), echoScale));
            gateway.handlePacket(reply);
        }
        void boundary(int marker) {
            // A preceding native receipt can arrive after the next write request was queued.
            BridgeEnvelope request = backend.stream().filter(Harness::stateRequest).reduce((a, b) -> b).orElseThrow();
            long id = requestId(request);
            control(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                    new BridgeControlMessage.Latency(BridgeControlMessage.Latency.BOUNDARY, id, marker).encode());
        }
        static boolean stateRequest(BridgeEnvelope p) {
            return p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT || p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT_BATCH
                    || p.kind() == BridgeEnvelope.Kind.TELEPORT_EMISSION;
        }
        static long requestId(BridgeEnvelope request) {
            return switch (request.kind()) {
                case ACTOR_CONTEXT -> ActorStateMessage.decode(request.body()).request();
                case ACTOR_CONTEXT_BATCH -> ActorStateBatch.decode(request.body()).request();
                default -> TeleportEmissionMessage.decode(request.body()).request();
            };
        }
        /** Every native state the gateway reported, whether sent alone or in a batch. */
        List<ActorStateMessage> states() {
            var states = new ArrayList<ActorStateMessage>();
            for (var p : backend) {
                if (p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT) states.add(ActorStateMessage.decode(p.body()));
                else if (p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT_BATCH) states.addAll(ActorStateBatch.decode(p.body()).states());
            }
            return states;
        }
        /** Answers every pending backend request and native receipt, in protocol order, until input flows. */
        boolean driveUntilInput() {
            var answered = new HashSet<BridgeEnvelope>(); int marker = 1000;
            for (int step = 0; step < 64; step++) {
                if (backend.stream().anyMatch(p -> p.kind() == BridgeEnvelope.Kind.CLIENT_PACKET)) return true;
                var request = backend.stream().filter(p -> stateRequest(p) && !answered.contains(p)).findFirst();
                if (request.isPresent()) { answered.add(request.get()); boundary(marker++); continue; }
                if (written.stream().noneMatch(p -> p instanceof NetworkStackLatencyPacket latency
                        && !acknowledged.contains(latency.getTimestamp()))) return false;
                ack();
            }
            return false;
        }
        PlayerAuthInputPacket input(long tick) {
            var input = new PlayerAuthInputPacket(); input.setTick(tick); input.setPosition(Vector3f.from(.5F, 83.62001F, .5F));
            input.setDelta(Vector3f.ZERO); input.setRotation(Vector3f.ZERO); input.setMotion(Vector2f.ZERO);
            input.setInputMode(InputMode.values()[0]); input.setPlayMode(ClientPlayMode.values()[0]);
            input.setInputInteractionModel(InputInteractionModel.values()[0]); return input;
        }
        void initialize() { challenge(); ack(); boundary(100); ack(); boundary(101); ack(); }
    }
    private static MobEffectPacket effect(int id, int amplifier, MobEffectPacket.Event event) {
        var packet = new MobEffectPacket(); packet.setRuntimeEntityId(42); packet.setEffectId(id);
        packet.setAmplifier(amplifier); packet.setDuration(200); packet.setEvent(event); return packet;
    }
    @Test public void crowdedNativeStateSharesBoundariesAndKeepsWireOrder() throws Exception {
        // A busy area: many state-carrying writes arrive while a boundary is still pending.
        var h = new Harness(); h.initialize();
        int start = h.backend.size(), writtenStart = h.written.size();
        var motions = new ArrayList<SetEntityMotionPacket>();
        for (int n = 0; n < 40; n++) {
            var motion = new SetEntityMotionPacket(); motion.setRuntimeEntityId(42); motion.setMotion(Vector3f.from(n, 0, 0));
            motions.add(motion); h.nativeSession.sendUpstreamPacket(motion);
        }
        var answered = new HashSet<BridgeEnvelope>(); int marker = 5000;
        for (int step = 0; step < 200; step++) {
            var pending = h.backend.subList(start, h.backend.size()).stream()
                    .filter(p -> Harness.stateRequest(p) && !answered.contains(p)).findFirst();
            if (pending.isPresent()) { answered.add(pending.get()); h.boundary(marker++); continue; }
            if (h.written.stream().noneMatch(p -> p instanceof NetworkStackLatencyPacket latency
                    && !h.acknowledged.contains(latency.getTimestamp()))) break;
            h.ack();
        }
        var sent = h.backend.subList(start, h.backend.size());
        long requests = sent.stream().filter(Harness::stateRequest).count();
        assertTrue("40 writes must share boundaries, not use one each: " + requests, requests <= 3);
        assertEquals(1, sent.stream().filter(p -> p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT_BATCH).limit(1).count());
        long receipts = sent.stream().filter(p -> p.kind() == BridgeEnvelope.Kind.LATENCY_RECEIPT).count();
        assertEquals("one SENT and one ACK per boundary", requests * 2, receipts);
        var reported = 0;
        for (var p : sent) {
            if (p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT) reported++;
            else if (p.kind() == BridgeEnvelope.Kind.ACTOR_CONTEXT_BATCH) reported += ActorStateBatch.decode(p.body()).states().size();
        }
        assertEquals(40, reported);
        var nativeOrder = h.written.subList(writtenStart, h.written.size()).stream()
                .filter(p -> p instanceof SetEntityMotionPacket).toList();
        assertEquals(motions, nativeOrder);
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void cultPingsBetweenNativeStatesKeepTheBackendReceiptOrder() throws Exception {
        // The backend places each boundary after the last receipt reported SENT, and a client ACK
        // consumes every earlier entry. A ping held inside a batch reached the client before the
        // batch's boundary while the backend had queued the boundary first.
        var h = new Harness(); h.initialize();
        int start = h.backend.size();
        var events = new ArrayList<Object[]>(); // {backend index, ping id} allocations
        var markers = new HashMap<Long, Integer>(); int marker = 7000;
        for (int n = 0; n < 6; n++) {
            var motion = new SetEntityMotionPacket(); motion.setRuntimeEntityId(42); motion.setMotion(Vector3f.from(n, 0, 0));
            h.nativeSession.sendUpstreamPacket(motion);
            if (n % 2 == 1) { events.add(new Object[]{h.backend.size(), -100 - n}); h.gateway.javaPing(-100 - n); }
        }
        var answered = new HashSet<BridgeEnvelope>();
        for (int step = 0; step < 100; step++) {
            var pending = h.backend.subList(start, h.backend.size()).stream()
                    .filter(p -> Harness.stateRequest(p) && !answered.contains(p)).findFirst();
            if (pending.isPresent()) {
                answered.add(pending.get()); markers.put(Harness.requestId(pending.get()), marker); h.boundary(marker++); continue;
            }
            if (h.written.stream().noneMatch(p -> p instanceof NetworkStackLatencyPacket latency
                    && !h.acknowledged.contains(latency.getTimestamp()))) break;
            h.ack();
        }
        // Replay the stream through the backend's ordering rule (CultPlayer.createBedrockTransactionAfterClientbound).
        var pendingIds = new LinkedList<Integer>(); var acked = new HashSet<Integer>(); Integer lastSent = null;
        int acks = 0;
        for (int i = start; i < h.backend.size(); i++) {
            for (var event : events) if ((int) event[0] == i) pendingIds.addLast((int) event[1]);
            var p = h.backend.get(i);
            if (Harness.stateRequest(p)) {
                int id = markers.get(Harness.requestId(p));
                if (lastSent == null || acked.contains(lastSent)) pendingIds.addFirst(id);
                else pendingIds.add(pendingIds.indexOf(lastSent) + 1, id);
            } else if (p.kind() == BridgeEnvelope.Kind.LATENCY_RECEIPT) {
                var receipt = BridgeControlMessage.Latency.decode(p.body());
                if (receipt.type() == BridgeControlMessage.Latency.SENT) lastSent = receipt.marker();
                else if (receipt.type() == BridgeControlMessage.Latency.ACK) {
                    assertTrue("ACK " + receipt.marker() + " was already consumed by a later receipt",
                            pendingIds.contains(receipt.marker()));
                    int head; do { head = pendingIds.removeFirst(); acked.add(head); } while (head != receipt.marker());
                    acks++;
                }
            }
        }
        assertTrue("every ping and boundary was acknowledged: " + acks, acks >= 3 + markers.size());
        assertTrue(pendingIds.isEmpty());
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void onFootCorrectionReachesTheClientAndReportsItsNativeState() throws Exception {
        var h = new Harness(); h.initialize();
        int start = h.backend.size();
        var correction = new CorrectionMessage(1, 0, -1, 42, 5, new AuthInputMessage.Double3(.5, 83, .5),
                new AuthInputMessage.Double3(0, -.0784, 0), 0, 0, true, 0, 0, 0, -1, null, false);
        h.control(BridgeEnvelope.Kind.SERVER_CORRECTION, correction.encode());
        h.boundary(9000); h.ack();
        var state = h.states().stream().filter(m -> m.kind() == ActorStateMessage.Kind.CORRECTION).reduce((a, b) -> b).orElseThrow();
        var reported = CorrectionMessage.decode(state.state());
        assertEquals(42, reported.runtimeId()); assertEquals(1, reported.sequence()); assertFalse(reported.vehicle());
        assertTrue(h.written.stream().anyMatch(p -> p instanceof CorrectPlayerMovePredictionPacket));
        assertTrue(h.backend.subList(start, h.backend.size()).stream().anyMatch(p -> p.kind() == BridgeEnvelope.Kind.LATENCY_RECEIPT
                && BridgeControlMessage.Latency.decode(p.body()).type() == BridgeControlMessage.Latency.ACK
                && BridgeControlMessage.Latency.decode(p.body()).marker() == 9000));
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void concurrentSendersReachTheBackendInSequenceOrder() throws Exception {
        // Inputs, backend replies and native writes call send() from different Geyser threads.
        var h = new Harness(); h.challenge();
        var loop = new io.netty.channel.DefaultEventLoop();
        try {
            when(h.nativeSession.getDownstream().getSession().getChannel().eventLoop()).thenReturn(loop);
            int before = h.backend.size();
            var threads = new ArrayList<Thread>();
            for (int t = 0; t < 4; t++) threads.add(new Thread(() -> {
                for (int n = 0; n < 500; n++) h.gateway.send(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                        new BridgeControlMessage.Latency(BridgeControlMessage.Latency.REQUEST, n, 0).encode());
            }));
            threads.forEach(Thread::start);
            for (var thread : threads) thread.join();
            loop.submit(() -> { }).sync();
            var received = h.backend.subList(before, h.backend.size());
            assertEquals(2000, received.size());
            for (int n = 1; n < received.size(); n++)
                assertEquals(received.get(n - 1).sequence() + 1, received.get(n).sequence());
        } finally { loop.shutdownGracefully().sync(); }
    }
    @Test public void inputForAVanishedVehicleIsForwardedWithoutDroppingTheSession() throws Exception {
        // A boat breaks while the client still predicts it for a tick.
        var h = new Harness(); h.initialize();
        when(h.nativeSession.getEntityCache().getEntityByGeyserId(999L)).thenReturn(null);
        var input = h.input(10); input.getInputData().add(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
        input.setPredictedVehicle(999L);
        h.gateway.handlePacket(input);
        var request = h.backend.getLast();
        assertEquals(BridgeEnvelope.Kind.CLIENT_PACKET, request.kind());
        var sent = AuthInputMessage.decode(request.body());
        assertEquals(999L, sent.vehicleRuntimeId()); assertEquals(-1, sent.vehicleJavaId());
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void effectRemovalsBeforeTheHandshakeDoNotBlockActivation() throws Exception {
        // A server switch clears effects with REMOVE events while the session is still unbound.
        var h = new Harness();
        h.nativeSession.sendUpstreamPacket(effect(1, 0, MobEffectPacket.Event.REMOVE));
        h.nativeSession.sendUpstreamPacket(effect(16, 0, MobEffectPacket.Event.ADD));
        h.challenge(); h.gateway.handlePacket(h.input(10));
        h.ack(); assertEquals(BridgeEnvelope.Kind.HELLO, h.backend.getFirst().kind());
        // The surviving night-vision effect is replayed as one more initial write with its own receipt.
        assertTrue(h.driveUntilInput());
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void boundEffectLevelsMatchTheLocalBridgeContract() throws Exception {
        var h = new Harness(); h.initialize();
        h.nativeSession.sendUpstreamPacket(effect(1, 0, MobEffectPacket.Event.ADD));
        var state = h.states().stream().filter(m -> m.kind() == ActorStateMessage.Kind.EFFECT)
                .reduce((a, b) -> b).orElseThrow();
        var effect = ActorStateMessage.Effect.decode(state.state());
        assertEquals(1, effect.id()); assertEquals(1, effect.level()); assertEquals(200, effect.duration());
    }
    @Test public void rawInputWaitsForHelloAndBothActualInitialWriteReceipts() throws Exception {
        var h = new Harness(); h.challenge(); var input = h.input(10); h.gateway.handlePacket(input);
        assertTrue(h.backend.isEmpty()); assertTrue(h.projected.isEmpty());
        h.ack(); assertEquals(BridgeEnvelope.Kind.HELLO, h.backend.getFirst().kind());
        assertFalse(h.backend.stream().anyMatch(p -> p.kind() == BridgeEnvelope.Kind.CLIENT_PACKET));
        h.boundary(100); h.ack(); h.boundary(101); h.ack();
        assertEquals(BridgeEnvelope.Kind.CLIENT_PACKET, h.backend.getLast().kind());
        assertTrue(h.projected.isEmpty());
    }
    @Test public void unscaledDeviceReceiptEchoesCompleteTheSameHandshake() throws Exception {
        var h = new Harness(); h.echoScale = 1; h.challenge(); h.gateway.handlePacket(h.input(10));
        h.ack(); assertEquals(BridgeEnvelope.Kind.HELLO, h.backend.getFirst().kind());
        h.boundary(100); h.ack(); h.boundary(101); h.ack();
        assertEquals(BridgeEnvelope.Kind.CLIENT_PACKET, h.backend.getLast().kind());
    }
    @Test public void rejectedInputDoesNotReachTheOriginalMovementTranslator() throws Exception {
        var h = new Harness(); h.initialize(); h.gateway.handlePacket(h.input(10));
        var request = h.backend.getLast();
        h.control(BridgeEnvelope.Kind.INPUT_RESULT, new BridgeControlMessage.InputResult(request.sequence(), false,
                new AuthInputMessage.Double3(.5, 82, .5), new AuthInputMessage.Double3(0, 0, 0),
                false, false, true, -1, false, 0, 10, false).encode());
        assertTrue(h.projected.isEmpty());
    }
    @Test public void challengeBeforeSpawnWaitsForActualNativePositionAndKeepsPingWireOrder() throws Exception {
        var h = new Harness(); h.outbound.clearSnapshot(); h.challenge();
        assertTrue(h.written.stream().noneMatch(p -> p instanceof NetworkStackLatencyPacket));
        h.gateway.javaPing(-12); // Allocated first, held until HELLO, so it must not be first in ACK order.
        var spawn = new MovePlayerPacket(); spawn.setRuntimeEntityId(42); spawn.setMode(MovePlayerPacket.Mode.RESPAWN);
        spawn.setPosition(Vector3f.from(.5F, 83.62001F, .5F)); spawn.setRotation(Vector3f.ZERO);
        h.nativeSession.sendUpstreamPacket(spawn);
        h.ack(); assertEquals(BridgeEnvelope.Kind.HELLO, h.backend.getFirst().kind());
        h.boundary(100); h.ack(); h.boundary(101); h.ack(); h.ack();
        assertTrue(h.backend.stream().anyMatch(p -> p.kind() == BridgeEnvelope.Kind.LATENCY_RECEIPT
                && BridgeControlMessage.Latency.decode(p.body()).marker() == -12
                && BridgeControlMessage.Latency.decode(p.body()).type() == BridgeControlMessage.Latency.ACK));
        verify(h.nativeSession, never()).disconnect(anyString());
    }
    @Test public void teleportHandleInputWaitsForItsRealNativeReceipt() throws Exception {
        var h = new Harness(); h.initialize();
        var teleport = new MovePlayerPacket(); teleport.setRuntimeEntityId(42);
        teleport.setPosition(Vector3f.from(.5F, 83.62001F, .5F));
        teleport.setRotation(Vector3f.ZERO); teleport.setMode(MovePlayerPacket.Mode.TELEPORT);
        h.nativeSession.sendUpstreamPacket(teleport);
        var input = h.input(30); input.getInputData().add(PlayerAuthInputData.HANDLE_TELEPORT);
        h.gateway.handlePacket(input);
        assertEquals(BridgeEnvelope.Kind.TELEPORT_EMISSION, h.backend.getLast().kind());
        h.boundary(102);
        assertFalse(h.backend.stream().anyMatch(p -> p.kind() == BridgeEnvelope.Kind.CLIENT_PACKET));
        h.ack();
        assertEquals(BridgeEnvelope.Kind.CLIENT_PACKET, h.backend.getLast().kind());
        assertTrue(AuthInputMessage.decode(h.backend.getLast().body()).tick() == 30);
    }
    @Test public void originalProjectionHappensBeforeFollowingAuthRequest() throws Exception {
        var h = new Harness(); h.initialize(); var first = h.input(10); var second = h.input(11);
        h.gateway.handlePacket(first); h.gateway.handlePacket(second);
        long request = h.backend.getLast().sequence();
        h.control(BridgeEnvelope.Kind.INPUT_RESULT, new BridgeControlMessage.InputResult(request, true,
                new AuthInputMessage.Double3(.5, 82, .5), new AuthInputMessage.Double3(0, 0, 0),
                false, true, true, -1, false, 0, 10, true).encode());
        assertEquals(List.of(first), h.projected);
        assertEquals(11, AuthInputMessage.decode(h.backend.getLast().body()).tick());
    }
    @Test public void economyUnboundInputUsesTheOriginalTranslatorWithoutBridgeTraffic() throws Exception {
        var h = new Harness(); var input = h.input(10); h.gateway.handlePacket(input);
        assertEquals(List.of(input), h.projected); assertTrue(h.backend.isEmpty());
    }
    @Test public void onlyAuthenticatedCultPingRegistrationClaimsNativePings() throws Exception {
        var h = new Harness(); h.initialize(); assertFalse(h.gateway.ownsPing(123));
        h.control(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                new BridgeControlMessage.Latency(BridgeControlMessage.Latency.PING_REGISTER, 0, -12).encode());
        assertTrue(h.gateway.ownsPing(-12)); assertFalse(h.gateway.ownsPing(-12));
    }
    @Test public void oldNativeReceiptAfterTransferCannotConsumeGeyserCallbacksOrNewInput() throws Exception {
        var h = new Harness(); h.challenge(); long old = h.lastReceipt().getTimestamp();
        h.gateway.javaLogin(); var stale = new NetworkStackLatencyPacket(); stale.setFromServer(false);
        stale.setTimestamp(old * NativeReceiptTimestamp.SCALE); h.gateway.handlePacket(stale);
        assertTrue(h.backend.isEmpty()); assertTrue(h.projected.isEmpty());
        h.gateway.handlePacket(h.input(99)); assertEquals(1, h.projected.size());
    }
}
