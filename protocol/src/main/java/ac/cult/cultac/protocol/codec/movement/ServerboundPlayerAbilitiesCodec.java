/*
 * Read layout adapted from PacketEvents WrapperPlayClientPlayerAbilities,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1 (1.16+ branch).
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAbilities;
import io.netty.buffer.ByteBuf;

public final class ServerboundPlayerAbilitiesCodec implements PacketCodec<ServerboundPlayerAbilities> {
    @Override
    public ServerboundPlayerAbilities read(ByteBuf input, ProtocolContext context) {
        return new ServerboundPlayerAbilities((input.readByte() & 0x02) != 0);
    }
}
