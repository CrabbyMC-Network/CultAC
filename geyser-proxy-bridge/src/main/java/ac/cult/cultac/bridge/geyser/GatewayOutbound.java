package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.session.GeyserSession;

/** Holds client-visible state until the backend has created its native receipt boundary. */
final class GatewayOutbound extends ChannelDuplexHandler implements AutoCloseable {
    private record Write(ChannelHandlerContext context, Object message, ChannelPromise promise, Runnable beforeWrite) { }
    private record Receipt(Runnable sent, boolean immediate) { }
    private record StateBoundary(byte[] state) { }
    private final GatewaySession owner;
    private final GeyserSession session;
    private final String name;
    private final ArrayDeque<Write> writes = new ArrayDeque<>();
    private final ArrayDeque<Write> initialWrites = new ArrayDeque<>();
    private final Map<Long, Receipt> ownReceipts = new HashMap<>();
    private final InitialActorSnapshot snapshot = new InitialActorSnapshot();
    private final GatewayEntityTransforms entityTransforms = new GatewayEntityTransforms();
    private final InitialEntityBindings initialBindings = new InitialEntityBindings();
    private final GatewayVehicleMetadata vehicleMetadata = new GatewayVehicleMetadata();
    private final IdentityHashMap<BedrockPacket, ActorStateMessage> bindingPackets = new IdentityHashMap<>();
    private final Set<BedrockPacket> preparedEffects = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BedrockPacket> initialPackets = Collections.newSetFromMap(new IdentityHashMap<>());
    // Initial replay writes awaiting each request's receipt (a batch can carry several).
    private final Map<Long, Integer> initialRequests = new HashMap<>();
    private final Set<Long> teleportRequests = new HashSet<>();
    private Runnable initialized;
    private int initialRemaining;
    private int initialReceiving;
    private boolean incompleteSnapshot;
    private final IdentityHashMap<BedrockPacket, TeleportEmissionMessage> setbackSources = new IdentityHashMap<>();
    private final IdentityHashMap<BedrockPacket, CorrectionMessage> correctionSources = new IdentityHashMap<>();
    // Writes held until the backend's boundary for waitingRequest; null when nothing is pending.
    private List<Write> waiting;
    private long request, waitingRequest, operation;
    private int javaTeleport = -1;
    private boolean emittingReceipt, closed;
    private GatewayBlockUpdates blockUpdates;

    GatewayOutbound(GatewaySession owner, GeyserSession session) {
        this.owner = owner; this.session = session;
        name = "cult-proxy-outbound-" + Integer.toHexString(System.identityHashCode(this));
    }
    void install() {
        var channel = session.getUpstream().getSession().getPeer().getChannel();
        channel.eventLoop().execute(() -> {
            if (!closed && channel.pipeline().get(name) == null)
                channel.pipeline().addLast(session.getTickEventLoop(), name, this);
        });
    }
    void javaTeleport(int id) { javaTeleport = id; }
    void ownReceipt(long timestamp, Runnable sent, boolean immediate) { ownReceipts.put(timestamp, new Receipt(sent, immediate)); }
    void clearSnapshot() { snapshot.clear(); initialBindings.clear(); entityTransforms.clear(); vehicleMetadata.clear(); incompleteSnapshot = false; }
    boolean initialSnapshotReady() { return snapshot.hasTeleport(); }
    void activate(Runnable initialized) {
        if (incompleteSnapshot) throw new IllegalStateException("Initial native state could not be captured");
        this.initialized = initialized;
        var packets = snapshot.replay(session.getPlayerEntity().geyserId(), owner.inputTick());
        packets.add(0, owner.collisionDefinition());
        var bindings = initialBindings.replay(); bindingPackets.putAll(bindings); packets.addAll(bindings.keySet());
        bindings.keySet().forEach(vehicleMetadata::prepareBinding);
        initialRemaining = packets.size(); initialReceiving = packets.size(); initialPackets.addAll(packets);
        packets.forEach(owner::initialPacket);
        drain();
    }
    @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
        Runnable beforeWrite = () -> { };
        boolean deferredReceipt = false;
        if (message instanceof BedrockPacketWrapper wrapper) {
            if (wrapper.getPacket() instanceof NetworkStackLatencyPacket latency) {
                Receipt receipt = ownReceipts.remove(latency.getTimestamp());
                if (receipt != null) {
                    beforeWrite = receipt.sent;
                    deferredReceipt = !receipt.immediate;
                    if (receipt.immediate) {
                        beforeWrite.run(); context.write(message, promise); context.flush();
                        if (emittingReceipt) { emittingReceipt = false; waiting = null; drain(); }
                        return;
                    }
                }
            }
        }
        if (!owner.active() || !owner.ready() && !deferredReceipt) {
            // Observing an unbound backend must never change its native Geyser behavior.
            try { observe(message); } catch (RuntimeException failure) {
                // The first capture failure decides the later handshake, so record exactly what failed.
                if (!incompleteSnapshot) owner.warn("Initial native state capture failed on "
                        + (message instanceof BedrockPacketWrapper wrapper ? wrapper.getPacket().getClass().getSimpleName()
                        : message.getClass().getSimpleName()), failure);
                incompleteSnapshot = true;
                if (owner.active()) owner.fail("Initial native state capture failed");
            }
            beforeWrite.run(); context.write(message, promise);
            owner.startHandshakeWhenReady(); return;
        }
        if (writes.size() >= 8192) {
            ReferenceCountUtil.release(message); promise.tryFailure(new IllegalStateException("Unbounded outbound bridge state"));
            owner.fail("Outbound state queue exceeded"); return;
        }
        Write next = new Write(context, message, promise, beforeWrite);
        if (message instanceof BedrockPacketWrapper wrapper && initialPackets.contains(wrapper.getPacket())) {
            initialWrites.add(next); initialReceiving--;
        } else writes.add(next);
        drain();
    }
    private boolean hasWrites() { return !initialWrites.isEmpty() || !writes.isEmpty(); }
    private Write peekWrite() { return initialWrites.isEmpty() ? writes.peekFirst() : initialWrites.peekFirst(); }
    private Write pollWrite() { return initialWrites.isEmpty() ? writes.removeFirst() : initialWrites.removeFirst(); }
    /** Writes that need their own boundary (or none) and therefore end a native state batch. */
    private boolean barrier(Write write) {
        if (write.message instanceof StateBoundary || !(write.message instanceof BedrockPacketWrapper wrapper)) return true;
        BedrockPacket packet = wrapper.getPacket();
        return packet instanceof GatewayBlockAckTranslator.Boundary
                || packet instanceof MovePlayerPacket move && move.getRuntimeEntityId() == session.getPlayerEntity().geyserId()
                && (move.getMode() == MovePlayerPacket.Mode.TELEPORT || move.getMode() == MovePlayerPacket.Mode.RESPAWN);
    }
    private void drain() {
        while (!closed && owner.ready() && waiting == null && initialReceiving == 0 && hasWrites()) {
            if (barrier(peekWrite())) drainBarrier(pollWrite());
            else drainBatch();
        }
    }
    private void drainBarrier(Write next) {
        if (next.message instanceof StateBoundary boundary) {
            waiting = List.of(next); waitingRequest = ++request;
            owner.send(BridgeEnvelope.Kind.ACTOR_CONTEXT,
                    new ActorStateMessage(waitingRequest, session.getPlayerEntity().geyserId(),
                            session.getPlayerEntity().getEntityId(), 0, ActorStateMessage.Kind.BLOCK_UPDATES, boundary.state).encode());
            return;
        }
        if (!(next.message instanceof BedrockPacketWrapper wrapper)) {
            observe(next.message); next.beforeWrite.run(); next.context.write(next.message, next.promise); return;
        }
        BedrockPacket packet = wrapper.getPacket();
        if (packet instanceof GatewayBlockAckTranslator.Boundary boundary) {
            ReferenceCountUtil.release(next.message); next.promise.trySuccess();
            if (boundary.start) {
                if (blockUpdates == null) blockUpdates = new GatewayBlockUpdates(session.getBlockMappings());
                blockUpdates.begin();
            }
            else {
                var batches = blockUpdates.end();
                for (int i = batches.size() - 1; i >= 0; i--)
                    writes.addFirst(new Write(next.context, new StateBoundary(batches.get(i).encode()), next.context.newPromise(), () -> { }));
            }
            return;
        }
        // Self teleport: its own TELEPORT_EMISSION boundary, never batched.
        if (blockUpdates != null) blockUpdates.capture(packet);
        preparedEffects.remove(packet);
        if (!owner.rewrite(packet)) { drop(next, packet); return; }
        long sequence = ++request;
        TeleportEmissionMessage teleport = teleport(packet, sequence);
        if (initialPackets.remove(packet)) initialRequests.merge(sequence, 1, Integer::sum);
        teleportRequests.add(sequence);
        waiting = List.of(next); waitingRequest = sequence;
        owner.send(BridgeEnvelope.Kind.TELEPORT_EMISSION, teleport.encode());
    }
    /**
     * Collects the run of native writes up to the next barrier. Stateful writes share one backend
     * boundary and one native receipt; stateless writes inside the run are held so nothing is
     * reordered. Each write is prepared exactly as a single write would be, in queue order.
     */
    private void drainBatch() {
        var held = new ArrayList<Write>();
        var states = new ArrayList<ActorStateMessage>();
        long batchRequest = ++request;
        int initial = 0, bytes = 0;
        while (hasWrites() && !barrier(peekWrite()) && states.size() < ActorStateBatch.MAX_STATES) {
            Write next = peekWrite();
            var wrapper = (BedrockPacketWrapper) next.message;
            BedrockPacket packet = wrapper.getPacket();
            if (packet instanceof MobEffectPacket effect && preparedEffects.add(packet)) {
                UpdateAttributesPacket movement = owner.beforeVehicleEffect(effect);
                if (movement != null) {
                    var pre = new Write(next.context, BedrockPacketWrapper.create(0,
                            wrapper.getSenderSubClientId(), wrapper.getTargetSubClientId(), movement, null),
                            next.context.newPromise(), () -> { });
                    if (initialWrites.peekFirst() == next) initialWrites.addFirst(pre); else writes.addFirst(pre);
                    continue;
                }
            }
            pollWrite(); preparedEffects.remove(packet);
            if (blockUpdates != null) blockUpdates.capture(packet);
            if (!owner.rewrite(packet)) { drop(next, packet); continue; }
            ActorStateMessage state = prepare(packet, batchRequest);
            if (state == null) {
                if (held.isEmpty()) {
                    observe(next.message); next.beforeWrite.run(); next.context.write(next.message, next.promise);
                    // A stateless initial replay (e.g. an effect the engine does not model) needs no receipt,
                    // but it still completes the initial set; otherwise input would wait forever.
                    if (initialPackets.remove(packet)) completeInitial(1);
                    continue;
                }
                held.add(next);
                if (initialPackets.remove(packet)) initial++;
                continue;
            }
            held.add(next); states.add(state); bytes += ActorStateBatch.encodedSize(state);
            if (initialPackets.remove(packet)) initial++;
            // Stop before the next state could overflow the envelope; it starts the following batch.
            if (bytes > ActorStateBatch.MAX_STATE_BYTES - BridgeEnvelope.MAX_BODY_BYTES / 4) break;
        }
        if (states.isEmpty()) {
            for (Write write : held) { observe(write.message); write.beforeWrite.run(); write.context.write(write.message, write.promise); }
            if (initial > 0) completeInitial(initial);
            return;
        }
        if (initial > 0) initialRequests.put(batchRequest, initial);
        waiting = held; waitingRequest = batchRequest;
        if (states.size() == 1) owner.send(BridgeEnvelope.Kind.ACTOR_CONTEXT, states.getFirst().encode());
        else owner.send(BridgeEnvelope.Kind.ACTOR_CONTEXT_BATCH, new ActorStateBatch(batchRequest, states).encode());
    }
    /** The backend state for one native write, prepared exactly as the unbatched path did. */
    private ActorStateMessage prepare(BedrockPacket packet, long sequence) {
        boolean binding = bindingPackets.containsKey(packet);
        ActorStateMessage state = bindingPackets.remove(packet);
        if (state != null) state = new ActorStateMessage(sequence, state.actorRuntimeId(), state.actorJavaId(),
                state.tick(), state.kind(), state.state());
        if (state == null) state = state(packet, sequence);
        if (state == null) state = entityTransforms.capture(session, packet, sequence);
        if (!binding) {
            var metadataState = vehicleMetadata.capture(session, packet, sequence, true,
                    state != null && state.kind() == ActorStateMessage.Kind.ENTITY_TRANSFORM && packet instanceof AddEntityPacket ? state : null);
            if (metadataState != null) state = metadataState;
        }
        if (state != null && packet instanceof AddEntityPacket entity && !entity.getAttributes().isEmpty()) {
            state = new ActorStateMessage(sequence, state.actorRuntimeId(), state.actorJavaId(), state.tick(),
                    ActorStateMessage.Kind.BATCH, new ActorStateMessage.Bundle(List.of(
                    new ActorStateMessage.Part(state.kind(), state.state()),
                    new ActorStateMessage.Part(ActorStateMessage.Kind.ATTRIBUTES, attributes(entity.getAttributes()).encode()))).encode());
        }
        return state;
    }
    private void drop(Write next, BedrockPacket packet) {
        ReferenceCountUtil.release(next.message); next.promise.trySuccess();
        if (initialPackets.remove(packet)) completeInitial(1);
    }
    private void completeInitial(int count) {
        if (count <= 0 || initialRemaining <= 0) return;
        initialRemaining -= count;
        if (initialRemaining <= 0) {
            initialRemaining = 0;
            Runnable callback = initialized; initialized = null;
            if (callback != null) callback.run();
        }
    }
    void boundary(BridgeControlMessage.Latency boundary) {
        if (boundary.type() != BridgeControlMessage.Latency.BOUNDARY || waiting == null
                || boundary.request() != waitingRequest || emittingReceipt)
            throw new IllegalArgumentException("Unknown native write boundary");
        long requestId = waitingRequest;
        List<Write> held = waiting;
        owner.send(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                new BridgeControlMessage.Latency(BridgeControlMessage.Latency.SENT, requestId, boundary.marker()).encode());
        for (Write exact : held) {
            observe(exact.message); exact.beforeWrite.run();
            if (exact.message instanceof StateBoundary) exact.promise.trySuccess();
            else exact.context.write(exact.message, exact.promise);
        }
        emittingReceipt = true;
        owner.sendBoundaryReceipt(() -> {
            owner.send(BridgeEnvelope.Kind.LATENCY_RECEIPT,
                    new BridgeControlMessage.Latency(BridgeControlMessage.Latency.ACK, requestId, boundary.marker(), owner.inputTick()).encode());
            if (teleportRequests.remove(requestId)) owner.resumeInput();
            Integer initial = initialRequests.remove(requestId);
            if (initial != null) completeInitial(initial);
        });
    }
    private void observe(Object message) {
        if (message instanceof BedrockPacketWrapper wrapper) {
            if (wrapper.getPacket() instanceof RemoveEntityPacket removal) vehicleMetadata.remove(removal.getUniqueEntityId());
            snapshot.observe(wrapper.getPacket(), session.getPlayerEntity().geyserId(), owner.inputTick());
            if (!owner.ready()) {
                ActorStateMessage transform = entityTransforms.capture(session, wrapper.getPacket(), 0);
                ActorStateMessage metadataState = vehicleMetadata.capture(session, wrapper.getPacket(), 0, false,
                        transform != null && wrapper.getPacket() instanceof AddEntityPacket ? transform : null);
                if (metadataState != null) transform = metadataState;
                initialBindings.observe(transform);
                if (wrapper.getPacket() instanceof AddEntityPacket add && transform != null && !add.getAttributes().isEmpty())
                    initialBindings.observe(new ActorStateMessage(0, transform.actorRuntimeId(), transform.actorJavaId(), 0,
                            ActorStateMessage.Kind.ATTRIBUTES, attributes(add.getAttributes()).encode()));
                else if (transform == null) {
                    ActorStateMessage nativeState = state(wrapper.getPacket(), 0);
                    if (nativeState != null && nativeState.actorRuntimeId() != session.getPlayerEntity().geyserId())
                        initialBindings.observe(nativeState);
                }
            }
        }
    }
    private static ActorStateMessage.Attributes attributes(List<org.cloudburstmc.protocol.bedrock.data.AttributeData> values) {
        return new ActorStateMessage.Attributes(values.stream().map(a -> new ActorStateMessage.Attribute(a.getName(), a.getValue(),
                a.getMinimum(), a.getMaximum(), a.getDefaultMinimum(), a.getDefaultMaximum(), a.getDefaultValue(),
                a.getModifiers().stream().map(m -> new ActorStateMessage.Modifier(m.getId(), m.getName(), m.getAmount(),
                        m.getOperation().ordinal(), m.getOperand(), m.isSerializable())).toList())).toList());
    }
    private TeleportEmissionMessage teleport(BedrockPacket packet, long requestId) {
        if (!(packet instanceof MovePlayerPacket move) || move.getRuntimeEntityId() != session.getPlayerEntity().geyserId()
                || (move.getMode() != MovePlayerPacket.Mode.TELEPORT && move.getMode() != MovePlayerPacket.Mode.RESPAWN)) return null;
        TeleportEmissionMessage source = setbackSources.remove(packet);
        Vector3f raw = move.getPosition();
        int javaId = source == null ? javaTeleport : source.sourceJavaTeleportId(); javaTeleport = -1;
        return new TeleportEmissionMessage(requestId, source == null ? ++operation : source.operation(),
                source == null ? 0 : source.provenance(), source == null ? -1 : source.setbackTransaction(),
                GatewayCoordinates.playerFeet(raw),
                new AuthInputMessage.Float3(raw.getX(), raw.getY(), raw.getZ()), move.getRotation().getY(),
                move.getRotation().getX(), move.isOnGround(), javaId, 0, 0, 0);
    }
    private ActorStateMessage state(BedrockPacket packet, long requestId) {
        long self = session.getPlayerEntity().geyserId();
        int javaId = session.getPlayerEntity().getEntityId();
        if (packet instanceof SetEntityLinkPacket link && link.getEntityLink().getTo() == self
                && link.getEntityLink().getType() != org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData.Type.REMOVE)
            return new ActorStateMessage(requestId, self, javaId, 0, ActorStateMessage.Kind.VEHICLE_MOUNT, new byte[0]);
        if (packet instanceof MovementEffectPacket effect && effect.getEntityRuntimeId() == self
                && effect.getEffectType() == org.cloudburstmc.protocol.bedrock.data.MovementEffectType.GLIDE_BOOST) {
            effect.setTick(session.getClientTicks());
            return new ActorStateMessage(requestId, self, javaId, effect.getTick(), ActorStateMessage.Kind.GLIDE_BOOST,
                    new ActorStateMessage.GlideBoost(effect.getDuration()).encode());
        }
        if (packet instanceof CorrectPlayerMovePredictionPacket value) {
            CorrectionMessage source = correctionSources.remove(packet);
            if (source == null) throw new IllegalArgumentException("Unknown native prediction correction");
            var actual = new CorrectionMessage(source.sequence(), source.generation(), source.vehicleJavaId(), source.runtimeId(),
                    value.getTick(), source.vehicle() ? new AuthInputMessage.Double3(value.getPosition().getX(),
                    value.getPosition().getY() - session.getPlayerEntity().getVehicle().getOffset(), value.getPosition().getZ())
                    : GatewayCoordinates.playerFeet(value.getPosition()),
                    new AuthInputMessage.Double3(value.getDelta().getX(), value.getDelta().getY(), value.getDelta().getZ()),
                    source.yaw(), source.pitch(), value.isOnGround(), 0, 0, 0, source.teleportTransaction(),
                    value.getVehicleAngularVelocity(), source.vehicle());
            return new ActorStateMessage(requestId, source.runtimeId(), source.vehicleJavaId(), value.getTick(),
                    ActorStateMessage.Kind.CORRECTION, actual.encode());
        }
        if (packet instanceof SetEntityDataPacket value && value.getRuntimeEntityId() == self) {
            var metadata = value.getMetadata();
            Vector3f collision = metadata.get(EntityDataTypes.COLLISION_BOX);
            var vehicle = session.getPlayerEntity().getVehicle();
            Vector3f seat = metadata.get(EntityDataTypes.SEAT_OFFSET);
            boolean flags = metadata.get(EntityDataTypes.FLAGS) != null;
            var state = new ActorStateMessage.Metadata(metadata.get(EntityDataTypes.WIDTH), metadata.get(EntityDataTypes.HEIGHT),
                    flags ? metadata.getFlag(EntityFlag.GLIDING) : null, flags ? metadata.getFlag(EntityFlag.CRAWLING) : null,
                    flags ? metadata.getFlag(EntityFlag.SWIMMING) : null, flags ? metadata.getFlag(EntityFlag.SNEAKING) : null,
                    flags ? metadata.getFlag(EntityFlag.DAMAGE_NEARBY_MOBS) : null,
                    metadata.get(EntityDataTypes.PLAYER_FLAGS) == null ? null : (metadata.get(EntityDataTypes.PLAYER_FLAGS) & 2) != 0,
                    flags ? metadata.getFlag(EntityFlag.USING_ITEM) : null, flags ? metadata.getFlag(EntityFlag.SPRINTING) : null,
                    flags ? (metadata.get(EntityDataTypes.FLAGS_2) == null ? 1 : 3) : 0,
                    collision == null ? null : collision.getX(), collision == null ? null : collision.getY(),
                    seat == null || !(vehicle instanceof org.geysermc.geyser.entity.type.BoatEntity) ? null
                            : new ActorStateMessage.Seat(vehicle.getEntityId(), vehicle.geyserId(),
                            new AuthInputMessage.Float3(seat.getX(), seat.getY(), seat.getZ())));
            return new ActorStateMessage(requestId, self, javaId, value.getTick(), ActorStateMessage.Kind.METADATA, state.encode());
        }
        if (packet instanceof SetEntityMotionPacket value) {
            long runtime = value.getRuntimeEntityId();
            var entity = runtime == self ? session.getPlayerEntity() : session.getEntityCache().getEntityByGeyserId(runtime);
            if (entity == null) return null;
            Vector3f vector = value.getMotion();
            return new ActorStateMessage(requestId, runtime, entity.getEntityId(), value.getTick(), ActorStateMessage.Kind.MOTION,
                    new ActorStateMessage.Vector(new AuthInputMessage.Double3(vector.getX(), vector.getY(), vector.getZ())).encode());
        }
        if (packet instanceof UpdateAttributesPacket value) {
            long runtime = value.getRuntimeEntityId();
            var entity = runtime == self ? session.getPlayerEntity() : session.getEntityCache().getEntityByGeyserId(runtime);
            if (entity == null) return null;
            var attributes = value.getAttributes().stream().map(a -> new ActorStateMessage.Attribute(a.getName(), a.getValue(),
                    a.getMinimum(), a.getMaximum(), a.getDefaultMinimum(), a.getDefaultMaximum(), a.getDefaultValue(),
                    a.getModifiers().stream().map(m -> new ActorStateMessage.Modifier(m.getId(), m.getName(), m.getAmount(),
                            m.getOperation().ordinal(), m.getOperand(), m.isSerializable())).toList())).toList();
            return new ActorStateMessage(requestId, runtime, entity.getEntityId(), value.getTick(), ActorStateMessage.Kind.ATTRIBUTES,
                    new ActorStateMessage.Attributes(attributes).encode());
        }
        if (packet instanceof MobEffectPacket value && value.getEvent() != MobEffectPacket.Event.NONE) {
            // Same contract as the backend's local GeyserReplayUpdate: unmodeled effects carry no movement
            // state, and the level is 1-based with 0 meaning removed.
            if (switch (value.getEffectId()) { case 1, 2, 8, 15, 24, 27, 33 -> false; default -> true; }) return null;
            long runtime = value.getRuntimeEntityId();
            var entity = runtime == self ? session.getPlayerEntity() : session.getEntityCache().getEntityByGeyserId(runtime);
            if (entity == null) return null;
            int level = value.getEvent() == MobEffectPacket.Event.REMOVE ? 0 : value.getAmplifier() + 1;
            return new ActorStateMessage(requestId, runtime, entity.getEntityId(), value.getTick(), ActorStateMessage.Kind.EFFECT,
                    new ActorStateMessage.Effect(value.getEffectId(), level, level == 0 ? -1 : value.getDuration()).encode());
        }
        if (packet instanceof SetPlayerGameTypePacket value) {
            return new ActorStateMessage(requestId, self, javaId, 0, ActorStateMessage.Kind.GAMEMODE,
                    new ActorStateMessage.GameMode(value.getGamemode()).encode());
        }
        return null;
    }
    void serverTeleport(byte[] body) {
        TeleportEmissionMessage request = TeleportEmissionMessage.decode(body);
        if (request.originX() != 0 || request.originZ() != 0 || request.originRevision() != 0)
            throw new IllegalArgumentException("Unsupported coordinate-origin adapter");
        MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(session.getPlayerEntity().geyserId());
        packet.setPosition(GatewayCoordinates.playerEye(request.feet()));
        packet.setRotation(Vector3f.from(request.pitch(), request.yaw(), request.yaw()));
        packet.setMode(MovePlayerPacket.Mode.TELEPORT); packet.setOnGround(request.onGround());
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
        setbackSources.put(packet, request);
        session.getPlayerEntity().setPosition(Vector3f.from(request.feet().x(), request.feet().y(), request.feet().z()));
        session.sendUpstreamPacket(packet);
    }
    void serverCorrection(byte[] body) {
        CorrectionMessage correction = CorrectionMessage.decode(body);
        if (correction.originX() != 0 || correction.originZ() != 0 || correction.originRevision() != 0)
            throw new IllegalArgumentException("Unsupported coordinate origin");
        var vehicle = correction.vehicle() ? session.getPlayerEntity().getVehicle() : null;
        if (correction.vehicle() && (vehicle == null || vehicle.getEntityId() != correction.vehicleJavaId()
                || vehicle.geyserId() != correction.runtimeId())) throw new IllegalArgumentException("Wrong correction vehicle");
        if (!correction.vehicle() && correction.runtimeId() != session.getPlayerEntity().geyserId())
            throw new IllegalArgumentException("Wrong correction actor");
        var packet = new CorrectPlayerMovePredictionPacket();
        packet.setPredictionType(correction.vehicle() ? PredictionType.VEHICLE : PredictionType.PLAYER);
        packet.setTick(correction.tick()); packet.setOnGround(correction.grounded());
        packet.setPosition(correction.vehicle() ? Vector3f.from(correction.feet().x(), correction.feet().y(), correction.feet().z()).up(vehicle.getOffset())
                : GatewayCoordinates.playerEye(correction.feet()));
        packet.setDelta(Vector3f.from(correction.velocity().x(), correction.velocity().y(), correction.velocity().z()));
        packet.setVehicleRotation(org.cloudburstmc.math.vector.Vector2f.from(correction.pitch(), correction.yaw()));
        if (correction.angularVelocity() != null) packet.setVehicleAngularVelocity(correction.angularVelocity());
        correctionSources.put(packet, correction); session.sendUpstreamPacket(packet);
    }
    boolean blocksInput() { return !teleportRequests.isEmpty(); }

    void reset() {
        if (waiting != null && !emittingReceipt) waiting.forEach(GatewayOutbound::discard);
        waiting = null; emittingReceipt = false;
        while (!writes.isEmpty()) discard(writes.removeFirst());
        while (!initialWrites.isEmpty()) discard(initialWrites.removeFirst());
        ownReceipts.clear(); setbackSources.clear(); correctionSources.clear(); javaTeleport = -1;
        entityTransforms.clear();
        initialPackets.clear(); initialRequests.clear(); initialized = null; initialRemaining = 0; initialReceiving = 0;
        teleportRequests.clear();
        bindingPackets.clear();
        preparedEffects.clear();
        if (blockUpdates != null) blockUpdates.reset();
    }
    private static void discard(Write write) {
        ReferenceCountUtil.release(write.message); write.promise.tryFailure(new IllegalStateException("Backend bridge changed"));
    }
    @Override public void close() {
        if (closed) return; closed = true; reset();
        var channel = session.getUpstream().getSession().getPeer().getChannel();
        channel.eventLoop().execute(() -> { if (channel.pipeline().get(name) == this) channel.pipeline().remove(name); });
    }
    private static double decimal(float value) { return Double.parseDouble(Float.toString(value)); }
}
