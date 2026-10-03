package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundCustomPayload;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

/** Paper exposes all serverbound custom payloads as raw DiscardedPayload data. */
public final class CustomPayloadCodec implements PacketCodec<ServerboundCustomPayload> {
    @Override
    public ServerboundCustomPayload read(ByteBuf input, ProtocolContext context) {
        String channel = Wire.readIdentifier(input);
        // Paper's ServerboundCustomPayloadPacket has an empty typed-payload list,
        // including minecraft:brand. DiscardedPayload enforces this bound in all
        // four pinned bundles; bytes after the identifier are the entire payload.
        int length = input.readableBytes();
        if (length > 32767) throw new MalformedPacketException("Custom payload exceeds 32767 bytes");
        byte[] data = new byte[length];
        input.readBytes(data);
        return new ServerboundCustomPayload(channel, data);
    }
}
