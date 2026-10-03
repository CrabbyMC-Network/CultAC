package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundRespawn;
import io.netty.buffer.ByteBuf;

public final class RespawnCodec implements PacketCodec<ClientboundRespawn> {
    @Override
    public ClientboundRespawn read(ByteBuf input, ProtocolContext context) {
        return new ClientboundRespawn(
                SpawnInfoCodec.read(input, context.version().atLeast(ProtocolVersion.V26_3)));
    }

    @Override
    public boolean readsEntirePayload() {
        return false;
    }
}
