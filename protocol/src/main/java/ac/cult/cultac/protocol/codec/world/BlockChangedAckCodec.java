/*
 * Layout adapted from PacketEvents WrapperPlayServerAcknowledgeBlockChanges,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundBlockChangedAck;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class BlockChangedAckCodec implements WritablePacketCodec<ClientboundBlockChangedAck> {
    @Override
    public ClientboundBlockChangedAck read(ByteBuf input, ProtocolContext context) {
        return new ClientboundBlockChangedAck(Wire.readVarInt(input));
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ClientboundBlockChangedAck packet) {
        Wire.writeVarInt(output, packet.sequence());
    }
}
