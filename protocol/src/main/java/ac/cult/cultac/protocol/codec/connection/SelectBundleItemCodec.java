/*
 * Layout adapted from PacketEvents WrapperPlayClientSelectBundleItem,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundSelectBundleItem;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class SelectBundleItemCodec implements PacketCodec<ServerboundSelectBundleItem> {
    @Override
    public ServerboundSelectBundleItem read(ByteBuf input, ProtocolContext context) {
        int slotId = Wire.readVarInt(input);
        int selectedItemIndex = Wire.readVarInt(input);
        if (context.version().atLeast(ProtocolVersion.V1_21_5) && selectedItemIndex < -1) {
            throw new MalformedPacketException("Invalid bundle selection " + selectedItemIndex);
        }
        return new ServerboundSelectBundleItem(slotId, selectedItemIndex);
    }
}
