package ac.cult.cultac.protocol;

import ac.cult.cultac.protocol.data.ProtocolData;
import io.netty.buffer.ByteBuf;

/** Stateless packet reader. The connection owns index/error discipline. */
public interface PacketCodec<R> {
    R read(ByteBuf input, ProtocolContext context);

    default void write(ByteBuf output, ProtocolContext context, R packet) {
        throw new UnsupportedOnVersionException("Read-only codec " + getClass().getName());
    }

    default boolean readsEntirePayload() {
        return true;
    }

    /** Validate version-dependent enum mappings and other prerequisites eagerly. */
    default void validate(ProtocolData data) {}
}
