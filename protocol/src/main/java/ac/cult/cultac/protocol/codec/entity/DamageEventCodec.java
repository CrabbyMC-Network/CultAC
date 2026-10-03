/*
 * Consumed prefix adapted from PacketEvents WrapperPlayServerDamageEvent,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundDamageEvent;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class DamageEventCodec implements PacketCodec<ClientboundDamageEvent> {
    @Override
    public ClientboundDamageEvent read(ByteBuf input, ProtocolContext context) {
        return new ClientboundDamageEvent(Wire.readVarInt(input));
    }

    // Only entityId is consumed. Forward the original damage source and position untouched.
    @Override
    public boolean readsEntirePayload() {
        return false;
    }
}
