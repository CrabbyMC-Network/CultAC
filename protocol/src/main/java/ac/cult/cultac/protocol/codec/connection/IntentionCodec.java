package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundIntention;
import ac.cult.cultac.protocol.value.ConnectionIntent;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class IntentionCodec implements PacketCodec<ServerboundIntention> {
    @Override
    public ServerboundIntention read(ByteBuf input, ProtocolContext context) {
        // All four pinned Papers raise vanilla's 255-character hostname read bound
        // to 32767 for proxy forwarding. Preserve every valid Paper-visible host.
        return new ServerboundIntention(
                Wire.readVarInt(input),
                Wire.readString(input, 32767),
                input.readUnsignedShort(),
                ConnectionIntent.fromWire(Wire.readVarInt(input)));
    }
}
