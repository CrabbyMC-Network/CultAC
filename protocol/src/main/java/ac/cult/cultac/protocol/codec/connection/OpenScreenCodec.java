/*
 * 26.3 layout adapted from PacketEvents WrapperPlayServerOpenWindow,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundOpenScreen;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class OpenScreenCodec implements PacketCodec<ClientboundOpenScreen> {
    @Override
    public ClientboundOpenScreen read(ByteBuf input, ProtocolContext context) {
        return new ClientboundOpenScreen(
                Wire.readVarInt(input),
                context.data().registry("minecraft:menu").name(Wire.readVarInt(input)));
    }

    // The title is unused; forwarding retains its original bytes without parsing a component.
    @Override
    public boolean readsEntirePayload() {
        return false;
    }
}
