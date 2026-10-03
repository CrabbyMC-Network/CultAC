/*
 * Read layout adapted from PacketEvents WrapperPlayServerEntityEffect,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundUpdateMobEffect;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class UpdateMobEffectCodec implements PacketCodec<ClientboundUpdateMobEffect> {
    @Override
    public ClientboundUpdateMobEffect read(ByteBuf input, ProtocolContext context) {
        int entityId = Wire.readVarInt(input);
        String effect = context.data().registry("minecraft:mob_effect").name(Wire.readVarInt(input));
        // Since 1.20.5, amplifier is a VarInt and there is no factor-data NBT tail.
        int amplifier = Wire.readVarInt(input);
        Wire.readVarInt(input); // Duration is not consumed; validate the complete packet.
        input.readByte(); // Flags, including unused bits, remain in the forwarded bytes.
        return new ClientboundUpdateMobEffect(entityId, effect, amplifier);
    }
}
