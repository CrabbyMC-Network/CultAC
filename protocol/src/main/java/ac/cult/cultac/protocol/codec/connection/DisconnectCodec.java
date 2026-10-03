package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundDisconnect;
import ac.cult.cultac.protocol.value.ByteArray;
import io.netty.buffer.ByteBuf;

/** The reason is the complete body; no consumer needs its component contents. */
public final class DisconnectCodec implements WritablePacketCodec<ClientboundDisconnect> {
    @Override public ClientboundDisconnect read(ByteBuf input, ProtocolContext context) {
        byte[] reason = new byte[input.readableBytes()];
        input.readBytes(reason);
        return new ClientboundDisconnect(new ByteArray(reason));
    }

    @Override public void write(ByteBuf output, ProtocolContext context, ClientboundDisconnect packet) {
        output.writeBytes(packet.reason().bytes());
    }
}
