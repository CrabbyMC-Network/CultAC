/*
 * Layout adapted from PacketEvents WrapperPlayServerTickingState,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundTickingState;
import io.netty.buffer.ByteBuf;

public final class TickingStateCodec implements PacketCodec<ClientboundTickingState> {
    @Override
    public ClientboundTickingState read(ByteBuf input, ProtocolContext context) {
        return new ClientboundTickingState(input.readFloat(), input.readBoolean());
    }
}
