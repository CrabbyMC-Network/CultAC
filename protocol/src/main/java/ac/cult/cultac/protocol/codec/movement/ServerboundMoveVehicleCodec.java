package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMoveVehicle;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class ServerboundMoveVehicleCodec implements PacketCodec<ServerboundMoveVehicle> {
    @Override public ServerboundMoveVehicle read(ByteBuf input, ProtocolContext context) {
        var position = Wire.readVec3(input);
        float yaw = input.readFloat(), pitch = input.readFloat();
        boolean hasGround = context.version().atLeast(ProtocolVersion.V1_21_4);
        return new ServerboundMoveVehicle(position, yaw, pitch, hasGround && input.readBoolean(), hasGround);
    }

}
