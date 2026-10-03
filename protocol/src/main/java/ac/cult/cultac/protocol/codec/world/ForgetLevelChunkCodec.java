/*
 * Layout adapted from PacketEvents WrapperPlayServerUnloadChunk,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundForgetLevelChunk;
import io.netty.buffer.ByteBuf;

public final class ForgetLevelChunkCodec implements PacketCodec<ClientboundForgetLevelChunk> {
    @Override
    public ClientboundForgetLevelChunk read(ByteBuf input, ProtocolContext context) {
        long packed = input.readLong();
        return new ClientboundForgetLevelChunk((int) packed, (int) (packed >>> 32));
    }
}
