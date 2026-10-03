package ac.cult.cultac.protocol.testing;

import ac.cult.cultac.protocol.*;
import io.netty.buffer.ByteBuf;

/** Test-only phase selection and index-preserving access to production codecs. */
public final class CodecFixture {
    private final ProtocolRuntime runtime;
    private ConnectionPhase serverboundPhase = ConnectionPhase.HANDSHAKE;
    private ConnectionPhase clientboundPhase = ConnectionPhase.HANDSHAKE;

    public CodecFixture(ProtocolRuntime runtime) {
        this.runtime = runtime;
    }

    public void phase(ConnectionPhase phase) {
        phase(PacketDirection.SERVERBOUND, phase);
        phase(PacketDirection.CLIENTBOUND, phase);
    }

    public void phase(PacketDirection direction, ConnectionPhase phase) {
        java.util.Objects.requireNonNull(phase);
        if (direction == PacketDirection.SERVERBOUND) serverboundPhase = phase;
        else clientboundPhase = phase;
    }

    public ConnectionPhase phase(PacketDirection direction) {
        return direction == PacketDirection.SERVERBOUND ? serverboundPhase : clientboundPhase;
    }

    private ProtocolRuntime.ResolvedPacket<?> binding(PacketDirection direction, int id) {
        return runtime.binding(phase(direction), direction, id);
    }

    /** Null means no catalog codec; transports must also check their per-ID listener route. */
    public PacketType<?> typeOf(PacketDirection direction, int id) {
        var bound = binding(direction, id);
        return bound == null ? null : bound.type();
    }

    /** Input starts after the packet ID. Neither its index nor reference count changes. */
    public Object read(PacketDirection direction, int id, ByteBuf input) {
        return runtime.decode(phase(direction), direction, id, input.duplicate());
    }

    public <R> R read(PacketType<R> type, int id, ByteBuf input) {
        if (typeOf(type.direction(), id) != type) {
            throw new ProtocolResolutionException("Packet ID " + id + " does not bind " + type);
        }
        return type.recordClass().cast(read(type.direction(), id, input));
    }

    public <R> void write(PacketType<R> type, R packet, ByteBuf output) {
        runtime.encode(phase(type.direction()), type, packet, output);
    }

    public void write(Object packet, ByteBuf output) {
        write(writableType(packet), packet, output);
    }

    public <R> PacketType<R> writableType(R packet) {
        return runtime.writableType(packet);
    }
}
