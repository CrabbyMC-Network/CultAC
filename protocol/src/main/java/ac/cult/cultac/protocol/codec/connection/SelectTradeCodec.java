/*
 * Layout adapted from PacketEvents WrapperPlayClientSelectTrade,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundSelectTrade;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class SelectTradeCodec implements PacketCodec<ServerboundSelectTrade> {
    @Override
    public ServerboundSelectTrade read(ByteBuf input, ProtocolContext context) {
        return new ServerboundSelectTrade(Wire.readVarInt(input));
    }
}
