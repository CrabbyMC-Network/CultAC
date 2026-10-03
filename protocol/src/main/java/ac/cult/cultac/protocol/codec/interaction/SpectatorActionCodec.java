/*
 * Layout adapted from PacketEvents WrapperPlayClientSpectateEntity and
 * PacketWrapper.readNullableVarInt, revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.interaction;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundSpectatorAction;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import java.util.OptionalInt;

public final class SpectatorActionCodec implements PacketCodec<ServerboundSpectatorAction> {
    @Override
    public ServerboundSpectatorAction read(ByteBuf input, ProtocolContext context) {
        int value = Wire.readVarInt(input);
        // V26_1 spectate_entity carries a raw entity ID; V26_2 makes it nullable.
        if (!context.version().atLeast(ProtocolVersion.V26_2)) {
            return new ServerboundSpectatorAction(OptionalInt.of(value));
        }
        return new ServerboundSpectatorAction(value == 0 ? OptionalInt.empty() : OptionalInt.of(value - 1));
    }
}
