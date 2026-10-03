package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerInput;
import io.netty.buffer.ByteBuf;

public final class PlayerInputCodec implements PacketCodec<ServerboundPlayerInput> {
    @Override
    public ServerboundPlayerInput read(ByteBuf input, ProtocolContext context) {
        int flags = input.readUnsignedByte();
        return new ServerboundPlayerInput(
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0,
                (flags & 8) != 0,
                (flags & 16) != 0,
                (flags & 32) != 0,
                (flags & 64) != 0);
    }
}
