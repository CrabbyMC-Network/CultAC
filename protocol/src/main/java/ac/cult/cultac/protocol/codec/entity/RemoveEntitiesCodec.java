/*
 * Read layout adapted from PacketEvents WrapperPlayServerDestroyEntities,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundRemoveEntities;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class RemoveEntitiesCodec implements PacketCodec<ClientboundRemoveEntities> {
    @Override
    public ClientboundRemoveEntities read(ByteBuf input, ProtocolContext context) {
        int count = Wire.readVarInt(input);
        // FriendlyByteBuf.readIntIdList's loop accepted negative counts before 26.3.
        if (count < 0 && !context.version().atLeast(ProtocolVersion.V26_3)) count = 0;
        return new ClientboundRemoveEntities(Wire.readVarIntList(input, count));
    }
}
