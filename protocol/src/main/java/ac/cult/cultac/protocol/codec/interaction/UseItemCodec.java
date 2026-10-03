/*
 * Layout adapted from PacketEvents WrapperPlayClientUseItem,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.interaction;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundUseItem;
import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class UseItemCodec implements PacketCodec<ServerboundUseItem> {
    @Override
    public ServerboundUseItem read(ByteBuf input, ProtocolContext context) {
        return new ServerboundUseItem(
                readHand(input, context.version().atLeast(ProtocolVersion.V26_3)),
                Wire.readVarInt(input),
                input.readFloat(),
                input.readFloat());
    }

    static Hand readHand(ByteBuf input, boolean zeroFallback) {
        int id = Wire.readVarInt(input);
        // 26.3 replaces strict readEnum with InteractionHand's ZERO-mapped codec.
        if (!zeroFallback && (id < 0 || id > 1)) {
            throw new MalformedPacketException("Invalid use-item hand " + id);
        }
        return id == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }
}
