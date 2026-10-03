/*
 * Layout adapted from PacketEvents WrapperPlayServerInitializeWorldBorder,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundInitializeBorder;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class InitializeBorderCodec implements PacketCodec<ClientboundInitializeBorder> {
    @Override
    public ClientboundInitializeBorder read(ByteBuf input, ProtocolContext context) {
        var border = new ClientboundInitializeBorder(
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                Wire.readVarLong(input),
                Wire.readVarInt(input));
        Wire.readVarInt(input); // Warning blocks.
        Wire.readVarInt(input); // Warning time.
        return border;
    }
}
