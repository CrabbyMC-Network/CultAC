package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import io.netty.util.ReferenceCountUtil;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

/** Every field and callback belongs to the Geyser session event loop. */
final class GatewaySession implements BedrockPacketHandler, AutoCloseable {
    private final GeyserSession session;
    private final BridgeEnvelopeCodec codec;
    private BedrockPacketHandler delegate;
    private BridgeReceiptOrder receipts;
    private UUID nonce;
    private Object downstream;
    private long nextSequence;
    // Bridge writes leave in sequence order: numbers are assigned and queued under one lock, and the
    // queue is only drained on the downstream channel's event loop. Geyser's sendDownstreamPacket
    // writes immediately on that loop but defers from any other thread, which could otherwise put
    // a later sequence on the wire first.
    private record Outgoing(UUID nonce, ServerboundCustomPayloadPacket packet) { }
    private final Object outgoingLock = new Object();
    private final ArrayDeque<Outgoing> outgoing = new ArrayDeque<>();
    private boolean ready, closed;
    private ValidatedInputQueue<BedrockPacket> queue;
    private PlayerAuthInputPacket waiting;
    private long waitingSequence;
    private final GatewayOutbound outbound;
    private final Map<Long, Runnable> latencyCallbacks = new HashMap<>();
    private final ArrayDeque<Long> latencyOrder = new ArrayDeque<>();
    private long latencyCounter;
    private boolean initialized;
    private boolean handshakeStarted;
    private long inputTick;
    private final java.util.Set<Integer> authorizedPings = new java.util.HashSet<>();
    private final GatewayPoseConfirmations poses = new GatewayPoseConfirmations();
    private final GatewaySprintAttributes sprintAttributes = new GatewaySprintAttributes();
    private final GatewayVehicleAttributes vehicleAttributes = new GatewayVehicleAttributes();
    private GatewaySprintAttributes.Boundary boundary = new GatewaySprintAttributes.Boundary(0, 0, false);

    GatewaySession(GeyserSession session, BridgeEnvelopeCodec codec) {
        this.session = session; this.codec = codec; this.outbound = new GatewayOutbound(this, session);
        newQueue();
    }
    void ensureInstalled() {
        BedrockPacketHandler current = session.getUpstream().getSession().getPacketHandler();
        if (current == this) return;
        if (delegate != null && nonce != null) { fail("Bedrock packet owner changed"); return; }
        if (current == null) return;
        delegate = current;
        session.getUpstream().getSession().setPacketHandler(this);
        outbound.install();
    }
    void resetBackend() {
        queue.close(); outbound.reset(); latencyCallbacks.clear(); latencyOrder.clear(); authorizedPings.clear();
        if (receipts != null) receipts.close();
        synchronized (outgoingLock) { nonce = null; nextSequence = 0; outgoing.clear(); }
        downstream = null; ready = false; initialized = false; waiting = null;
        handshakeStarted = false;
        poses.clear(); sprintAttributes.close(); vehicleAttributes.clear();
        boundary = new GatewaySprintAttributes.Boundary(0, 0, false);
        newQueue();
    }
    private void newQueue() { queue = new ValidatedInputQueue<>(256, this::dispatch, ReferenceCountUtil::release); }
    boolean active() {
        if (nonce != null && downstream != session.getDownstream()) resetBackend();
        return nonce != null && !closed;
    }
    boolean ready() { return active() && ready; }
    void startHandshakeWhenReady() {
        if (!active() || handshakeStarted || !outbound.initialSnapshotReady()) return;
        handshakeStarted = true;
        UUID connection = nonce;
        float width = session.getPlayerEntity().getBoundingBoxWidth();
        float height = session.getPlayerEntity().getBoundingBoxHeight();
        sendReceipt(() -> {
            if (!active() || !connection.equals(nonce)) return;
            send(BridgeEnvelope.Kind.HELLO, new BridgeControlMessage.Hello(session.protocolVersion(),
                    session.getPlayerEntity().geyserId(), 1, width, height).encode());
            ready = true;
            outbound.activate(() -> { initialized = true; resumeInput(); });
        });
    }
    void receive(byte[] bytes) {
        try {
            BridgeEnvelope message = codec.decode(bytes);
            if (!session.javaUuid().equals(message.player()) || message.direction() != BridgeEnvelope.Direction.TO_GATEWAY)
                throw new IllegalArgumentException("Wrong bridge owner");
            if (message.kind() == BridgeEnvelope.Kind.CHALLENGE) {
                if (message.sequence() != 0 || message.body().length != 0) throw new IllegalArgumentException("Invalid challenge");
                if (active()) throw new IllegalArgumentException("Duplicate challenge");
                synchronized (outgoingLock) { nonce = message.connection(); nextSequence = 0; outgoing.clear(); }
                downstream = session.getDownstream();
                receipts = new BridgeReceiptOrder(message.player(), nonce, message.direction(), 1);
                startHandshakeWhenReady();
                return;
            }
            if (!active() || !receipts.accept(message)) throw new IllegalArgumentException("Stale or reordered bridge control");
            switch (message.kind()) {
                case INPUT_RESULT -> result(BridgeControlMessage.InputResult.decode(message.body()));
                case LATENCY_RECEIPT -> {
                    var latency = BridgeControlMessage.Latency.decode(message.body());
                    if (latency.type() == BridgeControlMessage.Latency.PING_REGISTER) {
                        if (authorizedPings.size() >= 1024 || !authorizedPings.add(latency.marker()))
                            throw new IllegalArgumentException("Duplicate or unbounded native ping");
                    } else outbound.boundary(latency);
                }
                case SERVER_TELEPORT -> outbound.serverTeleport(message.body());
                case SERVER_CORRECTION -> outbound.serverCorrection(message.body());
                case CLOSE -> fail("Backend closed movement bridge");
                default -> throw new IllegalArgumentException("Unsupported bridge control");
            }
        } catch (RuntimeException failure) { fail("Invalid CultAC bridge control", failure); }
    }
    long send(BridgeEnvelope.Kind kind, byte[] body) {
        if (!active()) throw new IllegalStateException("No authenticated backend");
        long sequence;
        synchronized (outgoingLock) {
            if (nonce == null) throw new IllegalStateException("No authenticated backend");
            sequence = nextSequence++;
            outgoing.addLast(new Outgoing(nonce, new ServerboundCustomPayloadPacket(Key.key(BridgeEnvelopeCodec.CHANNEL),
                    codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND, kind,
                            session.javaUuid(), nonce, sequence, body)))));
        }
        flushOutgoing();
        return sequence;
    }
    private void flushOutgoing() {
        var downstreamSession = session.getDownstream();
        var channel = downstreamSession == null ? null : downstreamSession.getSession().getChannel();
        if (channel == null) return;
        if (!channel.eventLoop().inEventLoop()) { channel.eventLoop().execute(this::flushOutgoing); return; }
        while (true) {
            Outgoing next;
            synchronized (outgoingLock) {
                next = outgoing.pollFirst();
                if (next == null) return;
                // A queued message from a reset lease would break the new lease's sequence.
                if (!next.nonce().equals(nonce)) continue;
            }
            session.sendDownstreamPacket(next.packet());
        }
    }
    @Override public PacketSignal handlePacket(BedrockPacket packet) {
        ReferenceCountUtil.retain(packet);
        session.ensureInEventLoop(() -> {
            boolean transferred = false;
            try {
                if (closed) return;
                if (packet instanceof PlayerAuthInputPacket input) inputTick = input.getTick();
                long original = packet instanceof NetworkStackLatencyPacket latency
                        ? NativeReceiptTimestamp.pendingOriginal(latency.getTimestamp(), latencyCallbacks::containsKey)
                        : NativeReceiptTimestamp.NONE;
                // Retired connection receipt must never consume Geyser's FIFO ping callback.
                if (original == NativeReceiptTimestamp.NONE && packet instanceof NetworkStackLatencyPacket latency
                        && NativeReceiptTimestamp.reserved(latency.getTimestamp())) return;
                if (original != NativeReceiptTimestamp.NONE) {
                    Runnable receipt = latencyCallbacks.get(original);
                    // The client echoes NetworkStackLatency with fromServer=false; the reserved
                    // timestamp namespace and FIFO order are the proof, as in Geyser itself.
                    if (latencyOrder.isEmpty() || latencyOrder.removeFirst() != original)
                        throw new IllegalArgumentException("Reordered native receipt");
                    latencyCallbacks.remove(original); receipt.run(); return;
                }
                if (!active() || packet instanceof NetworkStackLatencyPacket) {
                    delegate.handlePacket(packet); return;
                }
                transferred = true;
                queue.offer(packet);
            } catch (RuntimeException failure) { fail("Bedrock input queue failed", failure); }
            finally { if (!transferred) ReferenceCountUtil.release(packet); }
        });
        return PacketSignal.HANDLED;
    }
    @Override public PacketSignal handle(PlayerAuthInputPacket packet) { return handlePacket(packet); }
    @Override public PacketSignal handle(MovePlayerPacket packet) { return handlePacket(packet); }
    @Override public PacketSignal handle(NetworkStackLatencyPacket packet) { return handlePacket(packet); }
    @Override public void onDisconnect(CharSequence reason) { close(); delegate.onDisconnect(reason); }
    private void dispatch(BedrockPacket packet) {
        if (packet instanceof PlayerAuthInputPacket input) {
            waiting = input;
            waitingSequence = -1;
            resumeInput();
        } else {
            queue.complete(packet, input -> project(input, () -> delegate.handlePacket(input)));
        }
    }
    void resumeInput() {
        if (initialized && !outbound.blocksInput() && waiting != null && waitingSequence == -1) request(waiting);
    }
    private void request(PlayerAuthInputPacket input) {
        waitingSequence = send(BridgeEnvelope.Kind.CLIENT_PACKET, AuthInputCapture.capture(session, input).encode());
        long exactSequence = waitingSequence;
        UUID exactNonce = nonce;
        session.scheduleInEventLoop(() -> {
            if (!closed && exactNonce.equals(nonce) && waiting == input && waitingSequence == exactSequence)
                fail("Backend input verdict timed out");
        }, 10, java.util.concurrent.TimeUnit.SECONDS);
    }
    private void result(BridgeControlMessage.InputResult result) {
        if (!ready || waiting == null || waitingSequence != result.requestSequence())
            throw new IllegalArgumentException("Wrong input verdict");
        PlayerAuthInputPacket original = waiting; waiting = null;
        GatewaySprintAttributes.Boundary previous = boundary;
        boundary = new GatewaySprintAttributes.Boundary(result.generation(), result.tick(), result.sprinting());
        queue.complete(original, input -> {
            if (!result.accepted()) {
                if (result.actionsConsumed()) poses.observeRejected(session, original);
                if (original.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)
                        && original.getItemStackRequest() != null)
                    project(original, () -> session.getPlayerInventoryHolder().translateRequests(java.util.List.of(original.getItemStackRequest())));
                return;
            }
            var vehicle = result.vehicleJavaId() == -1 ? null : session.getPlayerEntity().getVehicle();
            if (result.vehicleJavaId() != -1 && (vehicle == null || vehicle.getEntityId() != result.vehicleJavaId()))
                throw new IllegalArgumentException("Wrong vehicle projection");
            original.setPosition(vehicle == null ? GatewayCoordinates.playerEye(result.feet())
                    : Vector3f.from(result.feet().x(), result.feet().y(), result.feet().z()).up(vehicle.getOffset()));
            original.setDelta(Vector3f.from(result.velocity().x(), result.velocity().y(), result.velocity().z()));
            flag(original, PlayerAuthInputData.HORIZONTAL_COLLISION, result.horizontalCollision());
            flag(original, PlayerAuthInputData.VERTICAL_COLLISION, result.verticalCollision());
            poses.begin(session, original);
            if (previous.generation() != boundary.generation() || previous.sprinting() != boundary.sprinting())
                sprintAttributes.confirm(boundary, session.getPlayerEntity().geyserId(), session::sendUpstreamPacket);
            project(original, () -> delegate.handlePacket(original));
            poses.confirm(session, original, boundary);
        });
    }
    private void project(BedrockPacket packet, Runnable delegate) {
        if (!ready()) { delegate.run(); return; }
        GatewayInventoryActions.translate(session, packet, delegate, body -> send(BridgeEnvelope.Kind.INVENTORY_DIFF, body));
    }
    private static void flag(PlayerAuthInputPacket input, PlayerAuthInputData flag, boolean set) {
        if (set) input.getInputData().add(flag); else input.getInputData().remove(flag);
    }
    void javaTeleport(int id) { outbound.javaTeleport(id); }
    long generation() { return boundary.generation(); }
    void javaMovement(org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute attribute) {
        sprintAttributes.source(attribute, boundary, session.getPlayerEntity().geyserId(), session::sendUpstreamPacket);
    }
    void javaVehicleMovement(org.geysermc.geyser.entity.type.Entity entity,
                             org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute attribute) {
        var packet = vehicleAttributes.source(entity.geyserId(), attribute);
        ((org.geysermc.geyser.entity.vehicle.ClientVehicle) entity).getVehicleComponent().setMoveSpeed(packet.getAttributes().getFirst().getValue());
        session.sendUpstreamPacket(packet);
    }
    boolean rewrite(org.cloudburstmc.protocol.bedrock.packet.BedrockPacket packet) {
        if (packet instanceof UpdateAttributesPacket value) {
            if (value.getRuntimeEntityId() == session.getPlayerEntity().geyserId()) return sprintAttributes.rewrite(value, boundary);
            vehicleAttributes.written(value);
        } else if (packet instanceof SetEntityDataPacket value && value.getRuntimeEntityId() == session.getPlayerEntity().geyserId()) {
            Boolean confirmation = poses.confirmation(value, boundary.generation());
            if (Boolean.FALSE.equals(confirmation)) return false;
            if (confirmation == null) poses.rewriteOrdinary(value.getMetadata());
        }
        return true;
    }
    UpdateAttributesPacket beforeVehicleEffect(MobEffectPacket effect) {
        var entity = session.getEntityCache().getEntityByGeyserId(effect.getRuntimeEntityId());
        if (!(entity instanceof org.geysermc.geyser.entity.vehicle.ClientVehicle)) return null;
        return vehicleAttributes.beforeEffect(effect);
    }
    SetEntityDataPacket collisionDefinition() { return poses.collisionDefinition(session.getPlayerEntity().geyserId(), boundary.generation()); }
    void initialPacket(BedrockPacket packet) {
        if (packet instanceof SetEntityDataPacket metadata) poses.send(metadata, boundary.generation(), session::sendUpstreamPacket);
        else session.sendUpstreamPacket(packet);
    }
    long inputTick() { return inputTick; }
    void javaLogin() { resetBackend(); outbound.clearSnapshot(); }
    void sendReceipt(Runnable callback) {
        sendReceipt(() -> { }, callback, true);
    }
    void sendReceipt(Runnable sent, Runnable callback, boolean immediate) {
        sendReceipt(sent, callback, immediate, false);
    }
    void sendBoundaryReceipt(Runnable callback) {
        sendReceipt(() -> { }, callback, true, true);
    }
    private void sendReceipt(Runnable sent, Runnable callback, boolean immediate, boolean direct) {
        // Separate namespace from Geyser's ping IDs; our receipts never enter its FIFO callback cache.
        if (latencyCallbacks.size() >= 1024 || latencyCounter >= NativeReceiptTimestamp.MAX_COUNTER)
            throw new IllegalStateException("Unbounded native receipts");
        long timestamp = NativeReceiptTimestamp.marker(++latencyCounter);
        latencyCallbacks.put(timestamp, callback);
        NetworkStackLatencyPacket latency = new NetworkStackLatencyPacket();
        latency.setFromServer(true); latency.setTimestamp(timestamp);
        outbound.ownReceipt(timestamp, () -> {
            if (latencyCallbacks.containsKey(timestamp)) { latencyOrder.addLast(timestamp); sent.run(); }
        }, immediate);
        if (direct) session.getUpstream().getSession().sendPacketImmediately(latency);
        else session.sendUpstreamPacket(latency);
        UUID exactNonce = nonce;
        session.scheduleInEventLoop(() -> {
            if (!closed && java.util.Objects.equals(exactNonce, nonce) && latencyCallbacks.containsKey(timestamp))
                fail("Native client receipt timed out");
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }
    void javaPing(int id) {
        sendReceipt(() -> send(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                        new BridgeControlMessage.Latency(BridgeControlMessage.Latency.SENT, 0, id).encode()),
                () -> {
                    send(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                            new BridgeControlMessage.Latency(BridgeControlMessage.Latency.ACK, 0, id, inputTick).encode());
                    session.sendDownstreamPacket(new org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket(id));
                }, false);
    }
    boolean ownsPing(int id) { return active() && authorizedPings.remove(id); }
    void fail(String reason) { close(); session.disconnect("CultAC: " + reason + ". Please reconnect."); }
    private void fail(String reason, RuntimeException cause) {
        // Players only see the generic reason; operators need the exact failed check.
        warn(reason, cause);
        fail(reason);
    }
    void warn(String reason, RuntimeException cause) {
        try {
            var logger = session.getGeyser() == null ? null : session.getGeyser().getLogger();
            if (logger == null) return;
            var origin = new StringBuilder();
            for (var frame : cause.getStackTrace()) {
                if (origin.length() > 0 && !frame.getClassName().startsWith("ac.cult.")) continue;
                origin.append(origin.length() == 0 ? " at " : " < ").append(frame.getClassName().replaceFirst(".*\\.", ""))
                        .append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
                if (origin.length() > 300) break;
            }
            logger.warning("[cultacproxybridge] " + reason + " for " + session.bedrockUsername() + ": " + cause + origin);
        } catch (RuntimeException ignored) { }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; queue.close(); outbound.close(); latencyCallbacks.clear(); latencyOrder.clear();
        if (receipts != null) receipts.close();
        var nativeSession = session.getUpstream().getSession();
        if (nativeSession.getPacketHandler() == this) nativeSession.setPacketHandler(delegate);
    }
}
