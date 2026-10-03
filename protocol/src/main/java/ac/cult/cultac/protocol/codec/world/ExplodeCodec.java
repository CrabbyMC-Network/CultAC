/*
 * Layout adapted from PacketEvents WrapperPlayServerExplosion,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.world;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundExplode;
import ac.cult.cultac.protocol.value.Vec3d;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class ExplodeCodec implements PacketCodec<ClientboundExplode> {
    @Override
    public ClientboundExplode read(ByteBuf input, ProtocolContext context) {
        Vec3d center = Wire.readVec3(input);
        if (context.version().atLeast(ProtocolVersion.V1_21_9)) {
            input.readFloat(); // Radius, introduced in V1_21_9.
            input.readInt(); // Block count.
        }
        Vec3d knockback = input.readBoolean() ? Wire.readVec3(input) : Vec3d.ZERO;
        return new ClientboundExplode(center, knockback);
    }

    // The unconsumed suffix remains in the original forwarded packet.
    @Override
    public boolean readsEntirePayload() {
        return false;
    }
}
