package ac.cult.cultac.protocol.codec.entity;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundEntityMotion;
import ac.cult.cultac.protocol.value.Vec3d;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class EntityMotionCodec implements WritablePacketCodec<ClientboundEntityMotion> {
    @Override
    public ClientboundEntityMotion read(ByteBuf input, ProtocolContext context) {
        int id = Wire.readVarInt(input);
        // LpVec3 replaced short motion in V1_21_9.
        Vec3d velocity = context.version().atLeast(ProtocolVersion.V1_21_9)
                ? Wire.readLpVec3(input)
                : Wire.readShortVelocity(input);
        return new ClientboundEntityMotion(id, velocity);
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ClientboundEntityMotion packet) {
        Wire.writeVarInt(output, packet.entityId());
        if (context.version().atLeast(ProtocolVersion.V1_21_9)) Wire.writeLpVec3(output, packet.velocity());
        else Wire.writeShortVelocity(output, packet.velocity());
    }
}
