/*
 * Layout adapted from PacketEvents WrapperPlayClientPlayerBlockPlacement,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.interaction;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundUseItemOn;
import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.protocol.value.Vec3d;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class UseItemOnCodec implements PacketCodec<ServerboundUseItemOn> {
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public ServerboundUseItemOn read(ByteBuf input, ProtocolContext context) {
        var hand = UseItemCodec.readHand(input, context.version().atLeast(ProtocolVersion.V26_3));
        var position = Wire.readBlockPos(input);
        int face = Wire.readVarInt(input);
        if (context.version().atLeast(ProtocolVersion.V26_3)) {
            // Direction.STREAM_CODEC uses WRAP, including negative IDs.
            face = Math.floorMod(face, DIRECTIONS.length);
        } else if (face < 0 || face >= DIRECTIONS.length) {
            throw new MalformedPacketException("Invalid use-item face " + face);
        }
        // Match the float -> absolute double -> relative double path observed by
        // existing checks; tiny cursor values can round away at large coordinates.
        var cursor = new Vec3d(
                (position.x() + (double) input.readFloat()) - position.x(),
                (position.y() + (double) input.readFloat()) - position.y(),
                (position.z() + (double) input.readFloat()) - position.z());
        return new ServerboundUseItemOn(
                hand,
                position,
                DIRECTIONS[face],
                cursor,
                input.readBoolean(),
                input.readBoolean(),
                Wire.readVarInt(input));
    }
}
