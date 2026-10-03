package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerPosition;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

/** Since 1.21.2: teleport ID, full position/delta/rotation, then the relative bit set. */
public final class PlayerPositionCodec implements WritablePacketCodec<ClientboundPlayerPosition> {
    @Override public ClientboundPlayerPosition read(ByteBuf input, ProtocolContext context) {
        int id = Wire.readVarInt(input);
        var position = Wire.readVec3(input);
        var delta = Wire.readVec3(input);
        Wire.requireBytes(input, 8);
        float yaw = input.readFloat(), pitch = input.readFloat();
        return new ClientboundPlayerPosition(id, position, delta, yaw, pitch, Wire.readRelatives(input));
    }

    @Override public void write(ByteBuf output, ProtocolContext context, ClientboundPlayerPosition packet) {
        Wire.writeVarInt(output, packet.teleportId());
        Wire.writeVec3(output, packet.position());
        Wire.writeVec3(output, packet.delta());
        output.writeFloat(packet.yaw()).writeFloat(packet.pitch());
        Wire.writeRelatives(output, packet.relatives());
    }
}
