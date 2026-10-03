/*
 * Layout adapted from PacketEvents WrapperPlayServerBlockAction,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundBlockEvent;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class BlockEventCodec implements PacketCodec<ClientboundBlockEvent> {
    @Override
    public ClientboundBlockEvent read(ByteBuf input, ProtocolContext context) {
        var position = Wire.readBlockPos(input);
        int action = input.readUnsignedByte(), parameter = input.readUnsignedByte();
        int blockId = Wire.readVarInt(input);
        var blocks = context.data().registry("minecraft:block");
        // Vanilla's defaulted block registry resolves an unknown ID to air.
        if (blockId < 0 || blockId >= blocks.size()) blockId = blocks.id("minecraft:air");
        return new ClientboundBlockEvent(position, action, parameter, blockId);
    }
}
