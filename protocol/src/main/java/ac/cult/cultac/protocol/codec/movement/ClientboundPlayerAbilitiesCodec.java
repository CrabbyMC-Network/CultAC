/*
 * Read layout adapted from PacketEvents WrapperPlayServerPlayerAbilities,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerAbilities;
import io.netty.buffer.ByteBuf;

public final class ClientboundPlayerAbilitiesCodec implements PacketCodec<ClientboundPlayerAbilities> {
    @Override
    public ClientboundPlayerAbilities read(ByteBuf input, ProtocolContext context) {
        int flags = input.readByte();
        return new ClientboundPlayerAbilities(
                (flags & 0x01) != 0,
                (flags & 0x02) != 0,
                (flags & 0x04) != 0,
                (flags & 0x08) != 0,
                input.readFloat(),
                input.readFloat());
    }
}
