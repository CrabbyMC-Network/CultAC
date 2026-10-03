package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPong;
import io.netty.buffer.ByteBuf;

public final class PongCodec implements WritablePacketCodec<ServerboundPong> {
    @Override
    public ServerboundPong read(ByteBuf input, ProtocolContext context) {
        return new ServerboundPong(input.readInt());
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ServerboundPong packet) {
        output.writeInt(packet.id());
    }
}
