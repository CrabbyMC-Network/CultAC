package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPing;
import io.netty.buffer.ByteBuf;

public final class PingCodec implements WritablePacketCodec<ClientboundPing> {
    @Override
    public ClientboundPing read(ByteBuf input, ProtocolContext context) {
        return new ClientboundPing(input.readInt());
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ClientboundPing packet) {
        output.writeInt(packet.id());
    }
}
