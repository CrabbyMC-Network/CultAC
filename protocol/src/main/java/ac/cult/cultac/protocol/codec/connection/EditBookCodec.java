/*
 * Layout adapted from PacketEvents WrapperPlayClientEditBook (modern text fields),
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundEditBook;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Optional;

public final class EditBookCodec implements PacketCodec<ServerboundEditBook> {
    @Override
    public ServerboundEditBook read(ByteBuf input, ProtocolContext context) {
        int slot = Wire.readVarInt(input);
        int count = Wire.readLength(input, 100);
        var pages = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) pages.add(Wire.readString(input, 1024));
        var title = input.readBoolean() ? Optional.of(Wire.readString(input, 32)) : Optional.<String>empty();
        return new ServerboundEditBook(slot, pages, title);
    }
}
