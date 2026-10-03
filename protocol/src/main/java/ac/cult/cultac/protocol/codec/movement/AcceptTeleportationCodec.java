package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundAcceptTeleportation;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class AcceptTeleportationCodec implements PacketCodec<ServerboundAcceptTeleportation> {
    @Override
    public ServerboundAcceptTeleportation read(ByteBuf input, ProtocolContext context) {
        int id = Wire.readVarInt(input);
        if (!context.version().atLeast(ProtocolVersion.V26_3)) {
            return new ServerboundAcceptTeleportation(id, null, 0, 0);
        }
        var position = Wire.readVec3(input);
        return new ServerboundAcceptTeleportation(id, position, input.readFloat(), input.readFloat());
    }
}
