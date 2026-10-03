package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.UnsupportedOnVersionException;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerRotation;
import io.netty.buffer.ByteBuf;

public final class PlayerRotationCodec implements WritablePacketCodec<ClientboundPlayerRotation> {
    @Override public ClientboundPlayerRotation read(ByteBuf input, ProtocolContext context) {
        boolean relative = context.version().atLeast(ProtocolVersion.V1_21_9);
        float yaw = input.readFloat();
        boolean relativeYaw = relative && input.readBoolean();
        float pitch = input.readFloat();
        return new ClientboundPlayerRotation(yaw, relativeYaw, pitch, relative && input.readBoolean());
    }

    @Override public void write(ByteBuf output, ProtocolContext context, ClientboundPlayerRotation packet) {
        boolean relative = context.version().atLeast(ProtocolVersion.V1_21_9);
        if (!relative && (packet.relativeYaw() || packet.relativePitch())) {
            throw new UnsupportedOnVersionException("Relative player rotation is absent on " + context.version());
        }
        output.writeFloat(packet.yaw());
        if (relative) output.writeBoolean(packet.relativeYaw());
        output.writeFloat(packet.pitch());
        if (relative) output.writeBoolean(packet.relativePitch());
    }
}
