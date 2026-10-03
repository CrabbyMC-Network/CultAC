/*
 * Layout adapted from PacketEvents WrapperPlayServerPlayerInfoUpdate,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerInfoUpdate;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerInfoUpdate.*;
import ac.cult.cultac.protocol.value.ByteArray;
import ac.cult.cultac.protocol.value.GameMode;
import ac.cult.cultac.protocol.wire.NbtSkipper;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;

public final class PlayerInfoUpdateCodec implements WritablePacketCodec<ClientboundPlayerInfoUpdate> {
    @Override
    public ClientboundPlayerInfoUpdate read(ByteBuf input, ProtocolContext context) {
        int mask = input.readUnsignedByte();
        var actions = EnumSet.noneOf(Action.class);
        for (Action action : Action.values()) {
            if (action == Action.UPDATE_HAT && !context.version().atLeast(ProtocolVersion.V1_21_4)) continue;
            if ((mask & 1 << action.ordinal()) != 0) actions.add(action);
        }
        int count = Wire.readLength(input, input.readableBytes() / 16);
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) {
            var id = Wire.readUuid(input);
            GameMode mode = GameMode.SURVIVAL;
            var encoded = new EnumMap<Action, ByteArray>(Action.class);
            for (Action action : actions) {
                int start = input.readerIndex();
                switch (action) {
                    case ADD_PLAYER -> skipProfile(input);
                    case INITIALIZE_CHAT -> {
                        if (input.readBoolean()) {
                            input.skipBytes(24); // Session UUID and key expiry.
                            skipBytes(input, 512);
                            skipBytes(input, 4096);
                        }
                    }
                    case UPDATE_GAME_MODE -> {
                        int value = Wire.readVarInt(input);
                        mode = value >= 0 && value < 4 ? GameMode.values()[value] : GameMode.SURVIVAL;
                    }
                    case UPDATE_LISTED, UPDATE_HAT -> input.skipBytes(1);
                    case UPDATE_LATENCY, UPDATE_LIST_ORDER -> Wire.readVarInt(input);
                    case UPDATE_DISPLAY_NAME -> {
                        if (input.readBoolean()) {
                            NbtSkipper.skip(input, 512);
                        }
                    }
                }
                if (action != Action.UPDATE_GAME_MODE) {
                    byte[] bytes = new byte[input.readerIndex() - start];
                    input.getBytes(start, bytes);
                    encoded.put(action, new ByteArray(bytes));
                }
            }
            entries.add(new Entry(id, mode, encoded));
        }
        return new ClientboundPlayerInfoUpdate(actions, entries);
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ClientboundPlayerInfoUpdate packet) {
        int mask = 0;
        for (Action action : packet.actions()) {
            if (action == Action.UPDATE_HAT && !context.version().atLeast(ProtocolVersion.V1_21_4)) {
                throw new IllegalArgumentException("Player-info hat action is absent on 1.21.3");
            }
            mask |= 1 << action.ordinal();
        }
        output.writeByte(mask);
        Wire.writeVarInt(output, packet.entries().size());
        for (Entry entry : packet.entries()) {
            Wire.writeUuid(output, entry.profileId());
            for (Action action : Action.values()) if (packet.actions().contains(action)) {
                if (action == Action.UPDATE_GAME_MODE) Wire.writeVarInt(output, entry.gameMode().ordinal());
                else {
                    ByteArray bytes = entry.encodedActions().get(action);
                    if (bytes == null) throw new IllegalArgumentException("Missing retained player-info action " + action);
                    output.writeBytes(bytes.bytes());
                }
            }
        }
    }

    private static void skipProfile(ByteBuf input) {
        skipBytes(input, 16 * 3);
        // The native property codec accepts a negative count as an empty map.
        int count = Wire.readVarInt(input);
        if (count > 16) throw new MalformedPacketException("Too many profile properties");
        for (int i = 0; i < count; i++) {
            skipBytes(input, 64 * 3);
            skipBytes(input, 32767 * 3);
            if (input.readBoolean()) skipBytes(input, 1024 * 3);
        }
    }

    private static void skipBytes(ByteBuf input, int maximum) {
        input.skipBytes(Wire.readLength(input, maximum));
    }
}
