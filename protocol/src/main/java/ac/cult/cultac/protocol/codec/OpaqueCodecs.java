package ac.cult.cultac.protocol.codec;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.Opaque;
import io.netty.buffer.ByteBuf;

/** Payload policies for families Cult routes by identity alone; each decodes to the family's shared value. */
public final class OpaqueCodecs {
    /** The payload must be empty; the runtime rejects trailing bytes. */
    public static final PacketCodec<Opaque> EMPTY =
            (input, context) -> context.type().opaqueValue();

    /** An empty payload Cult also writes. */
    public static final WritablePacketCodec<Opaque> WRITABLE_EMPTY = new WritablePacketCodec<>() {
        @Override
        public Opaque read(ByteBuf input, ProtocolContext context) {
            return context.type().opaqueValue();
        }

        @Override
        public void write(ByteBuf output, ProtocolContext context, Opaque packet) {}
    };

    /** The payload has no consumer and stays unread. */
    public static final PacketCodec<Opaque> IGNORED = new PacketCodec<>() {
        @Override
        public Opaque read(ByteBuf input, ProtocolContext context) {
            return context.type().opaqueValue();
        }

        @Override
        public boolean readsEntirePayload() {
            return false;
        }
    };

    private OpaqueCodecs() {}
}
