package ac.cult.cultac.protocol;

import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One wire/model boundary; pure client actions retain their source-version schema and ordering. */
public final class ProjectedPackets implements AutoCloseable {
    public record Value(PacketType<?> type, Object packet, boolean derived) {}
    /** Owns its buffers until they are transferred to the transport. */
    public record Encoded(List<ByteBuf> frames, boolean destinationObserved) {}

    private final ProtocolRuntime wire, model;
    private final Supplier<PacketProjection> factory;
    private PacketProjection projection;
    private boolean closed;

    public ProjectedPackets(ProtocolRuntime wire, ProtocolRuntime model, Supplier<PacketProjection> factory) {
        this.wire = Objects.requireNonNull(wire);
        this.model = Objects.requireNonNull(model);
        this.factory = Objects.requireNonNull(factory);
    }

    public ProtocolRuntime wire() {
        return wire;
    }

    public boolean translated() {
        return wire.data().version() != model.data().version();
    }

    /** Borrows the physical buffer; only the caller may cancel, replace or forward it. */
    public List<Value> read(
            ConnectionPhase phase,
            PacketDirection direction,
            ByteBuf physical,
            CodecState state,
            Predicate<PacketType<?>> consumed,
            boolean mirror) {
        if (!translated()) {
            Value value = decode(model, phase, direction, physical, state, consumed, false);
            return value == null ? List.of() : List.of(value);
        }
        var source = wire.binding(phase, direction, Wire.peekVarInt(physical));
        String sourceName = wire.data().packets(phase, direction).name(Wire.peekVarInt(physical));
        boolean pure = source != null && !source.type().codec().requiresModelValues();
        Value original = pure ? decode(wire, phase, direction, physical, state, consumed, false) : null;
        var values = new ArrayList<Value>();
        boolean sourceAdded = false;
        for (var frame : projection().toModel(direction, phase, ByteBufUtil.getBytes(physical), mirror)) {
            if (frame.direction() != direction || frame.phase() != phase)
                throw new ProtocolResolutionException("Projection changed physical frame direction or phase");
            ByteBuf bytes = Unpooled.wrappedBuffer(frame.bytes());
            try {
                var binding = model.binding(phase, direction, Wire.peekVarInt(bytes));
                if (pure && !frame.generated() && binding != null && binding.type() == source.type()) {
                    if (original != null) values.add(original);
                    sourceAdded = true;
                    continue;
                }
                if (binding == null) continue;
                // Generated confirmations/movement belong to the converter's mirror,
                // never to the real client. Derived native registry/value packets are
                // needed by the model and keep their order before the physical frame.
                if ((pure || frame.generated()) && !binding.type().codec().requiresModelValues()) continue;
                boolean derived = pure
                        || (frame.generated() && !binding.type().wireNames().contains(sourceName));
                Value value = decode(model, phase, direction, bytes, state, consumed, derived);
                if (value != null) values.add(value);
            } finally {
                bytes.release();
            }
        }
        // An old ID-only teleport acknowledgment can be consumed by ViaBackwards
        // while waiting for its position response. Cult still observes that action.
        if (pure && !sourceAdded && original != null) values.add(original);
        return List.copyOf(values);
    }

    private static Value decode(
            ProtocolRuntime runtime,
            ConnectionPhase phase,
            PacketDirection direction,
            ByteBuf bytes,
            CodecState state,
            Predicate<PacketType<?>> consumed,
            boolean derived) {
        ByteBuf view = bytes.duplicate();
        int id = Wire.readVarInt(view);
        var binding = runtime.binding(phase, direction, id);
        if (binding == null || !consumed.test(binding.type())) return null;
        return new Value(binding.type(), runtime.decode(phase, direction, id, view, state), derived);
    }

    /** Source codecs handle pure packets; only native-value packets use reverse conversion. */
    public <R> Encoded encode(
            ConnectionPhase phase, PacketType<R> type, R packet, ByteBufAllocator allocator, CodecState state) {
        boolean nativeValues = translated() && (type.codec().requiresModelValues() || !wire.supports(type));
        ByteBuf bytes = allocator.buffer();
        try {
            (nativeValues ? model : wire).encode(phase, type, packet, bytes, state);
            if (!nativeValues) {
                var result = new Encoded(List.of(bytes), false);
                bytes = null;
                return result;
            }
            var frames = new ArrayList<ByteBuf>();
            try {
                for (var frame : projection().toWire(type.direction(), phase, ByteBufUtil.getBytes(bytes))) {
                    if (frame.direction() != type.direction() || frame.phase() != phase)
                        throw new ProtocolResolutionException("Authored conversion changed packet direction or phase");
                    frames.add(Unpooled.wrappedBuffer(frame.bytes()));
                }
                return new Encoded(List.copyOf(frames), true);
            } catch (Throwable failure) {
                frames.forEach(ByteBuf::release);
                throw failure;
            }
        } finally {
            if (bytes != null) bytes.release();
        }
    }

    public <R> PacketType<R> writableType(R packet) {
        return model.writableType(packet);
    }

    private PacketProjection projection() {
        if (closed) throw new IllegalStateException("Packet projection is closed");
        if (projection == null) projection = Objects.requireNonNull(factory.get(), "Missing wire/model converter");
        return projection;
    }

    @Override
    public void close() {
        closed = true;
        if (projection != null) {
            projection.close();
            projection = null;
        }
    }
}
