package ac.cult.cultac.protocol;

import io.netty.buffer.ByteBuf;

/** Requires an encoder at compile time for every writable catalog entry. */
public interface WritablePacketCodec<R> extends PacketCodec<R> {
    void write(ByteBuf output, ProtocolContext context, R packet);
}
