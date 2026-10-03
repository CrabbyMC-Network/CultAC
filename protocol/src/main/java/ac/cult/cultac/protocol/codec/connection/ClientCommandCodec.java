/*
 * Layout adapted from PacketEvents WrapperPlayClientClientStatus,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundClientCommand;
import ac.cult.cultac.protocol.value.ClientCommandAction;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class ClientCommandCodec implements PacketCodec<ServerboundClientCommand> {
    private static final ClientCommandAction[] ACTIONS = ClientCommandAction.values();

    @Override
    public ServerboundClientCommand read(ByteBuf input, ProtocolContext context) {
        // V26_1 adds REQUEST_GAMERULE_VALUES; the removed inventory action is not ordinal 2.
        int count = context.version().atLeast(ProtocolVersion.V26_1) ? 3 : 2;
        return new ServerboundClientCommand(ACTIONS[Wire.readEnumOrdinal(input, count)]);
    }
}
