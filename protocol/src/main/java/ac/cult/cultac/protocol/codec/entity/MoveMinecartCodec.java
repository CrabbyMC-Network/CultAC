/*
 * Reads adapted from PacketEvents WrapperPlayServerMoveMinecart and MinecartStep,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2024 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundMoveMinecart;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;

public final class MoveMinecartCodec implements PacketCodec<ClientboundMoveMinecart> {
    @Override public ClientboundMoveMinecart read(ByteBuf input, ProtocolContext context) {
        int id = Wire.readVarInt(input), count = Wire.readVarInt(input);
        if (count < 0 || count > input.readableBytes() / 54) throw new MalformedPacketException("Invalid minecart step count " + count);
        var steps = new ArrayList<ClientboundMoveMinecart.Step>(count);
        for (int i = 0; i < count; i++) steps.add(new ClientboundMoveMinecart.Step(Wire.readVec3(input), Wire.readVec3(input),
                Wire.readAngle(input), Wire.readAngle(input), input.readFloat()));
        return new ClientboundMoveMinecart(id, steps);
    }
}
