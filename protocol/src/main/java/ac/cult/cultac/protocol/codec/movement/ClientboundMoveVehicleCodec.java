package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundMoveVehicle;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

/** PositionAndRotation replaced native fields in 26.3; the three doubles/two floats stayed identical. */
public final class ClientboundMoveVehicleCodec implements WritablePacketCodec<ClientboundMoveVehicle> {
    @Override public ClientboundMoveVehicle read(ByteBuf input, ProtocolContext context) {
        var position = Wire.readVec3(input);
        Wire.requireBytes(input, 8);
        return new ClientboundMoveVehicle(position, input.readFloat(), input.readFloat());
    }

    @Override public void write(ByteBuf output, ProtocolContext context, ClientboundMoveVehicle packet) {
        Wire.writeVec3(output, packet.position());
        output.writeFloat(packet.yaw()).writeFloat(packet.pitch());
    }
}
