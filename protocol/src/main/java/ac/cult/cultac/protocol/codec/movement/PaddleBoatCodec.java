/*
 * Layout adapted from PacketEvents WrapperPlayClientSteerBoat,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPaddleBoat;
import io.netty.buffer.ByteBuf;

public final class PaddleBoatCodec implements WritablePacketCodec<ServerboundPaddleBoat> {
    @Override
    public ServerboundPaddleBoat read(ByteBuf input, ProtocolContext context) {
        return new ServerboundPaddleBoat(input.readBoolean(), input.readBoolean());
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ServerboundPaddleBoat packet) {
        output.writeBoolean(packet.left());
        output.writeBoolean(packet.right());
    }
}
