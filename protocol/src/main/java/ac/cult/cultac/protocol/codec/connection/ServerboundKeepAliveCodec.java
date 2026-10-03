/*
 * Layout adapted from PacketEvents WrapperPlayClientKeepAlive,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundKeepAlive;
import io.netty.buffer.ByteBuf;

public final class ServerboundKeepAliveCodec implements PacketCodec<ServerboundKeepAlive> {
    @Override
    public ServerboundKeepAlive read(ByteBuf input, ProtocolContext context) {
        return new ServerboundKeepAlive(input.readLong());
    }
}
