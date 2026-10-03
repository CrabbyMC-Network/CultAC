package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundTeleportEntity;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

/** Since 1.21.2: entity ID, full position/delta/rotation, relatives and ground flag. */
public final class TeleportEntityCodec implements WritablePacketCodec<ClientboundTeleportEntity> {
    @Override
    public ClientboundTeleportEntity read(ByteBuf input, ProtocolContext context) {
        int id = Wire.readVarInt(input);
        var position = Wire.readVec3(input);
        var delta = Wire.readVec3(input);
        Wire.requireBytes(input, 8);
        float yaw = input.readFloat(), pitch = input.readFloat();
        var relatives = Wire.readRelatives(input);
        Wire.requireBytes(input, 1);
        return new ClientboundTeleportEntity(id, position, delta, yaw, pitch, relatives, input.readBoolean());
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ClientboundTeleportEntity packet) {
        Wire.writeVarInt(output, packet.entityId());
        Wire.writeVec3(output, packet.position());
        Wire.writeVec3(output, packet.delta());
        output.writeFloat(packet.yaw()).writeFloat(packet.pitch());
        Wire.writeRelatives(output, packet.relatives());
        output.writeBoolean(packet.onGround());
    }
}
