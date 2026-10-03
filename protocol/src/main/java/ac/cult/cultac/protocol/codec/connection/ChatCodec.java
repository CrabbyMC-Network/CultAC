/*
 * Layout adapted from PacketEvents WrapperPlayClientChatMessage and
 * PacketWrapper.readLastSeenMessagesUpdate, revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundChat;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class ChatCodec implements PacketCodec<ServerboundChat> {
    @Override
    public ServerboundChat read(ByteBuf input, ProtocolContext context) {
        return new ServerboundChat(Wire.readString(input, 256));
    }

    // Forward the original body; signing fields are validated by the server decoder.
    @Override
    public boolean readsEntirePayload() {
        return false;
    }
}
