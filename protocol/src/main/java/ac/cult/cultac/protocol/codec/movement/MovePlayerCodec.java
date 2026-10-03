package ac.cult.cultac.protocol.codec.movement;

import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.VariantCodec;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;

/** All four variants use the flags byte introduced in 1.21.2. */
public final class MovePlayerCodec
        implements WritablePacketCodec<ServerboundMovePlayer>, VariantCodec<ServerboundMovePlayer> {
    private enum Variant {
        POS("move_player_pos", true, false),
        POS_ROT("move_player_pos_rot", true, true),
        ROT("move_player_rot", false, true),
        STATUS_ONLY("move_player_status_only", false, false);

        private final String wireName;
        private final boolean position;
        private final boolean rotation;

        Variant(String wireName, boolean position, boolean rotation) {
            this.wireName = wireName;
            this.position = position;
            this.rotation = rotation;
        }
    }

    private static final Variant[] VARIANTS = Variant.values();
    private static final List<String> NAMES =
            Arrays.stream(VARIANTS).map(variant -> variant.wireName).toList();

    @Override
    public List<String> variants() {
        return NAMES;
    }

    @Override
    public int variantOf(ServerboundMovePlayer packet) {
        if (packet.hasPosition()) return (packet.hasRotation() ? Variant.POS_ROT : Variant.POS).ordinal();
        return (packet.hasRotation() ? Variant.ROT : Variant.STATUS_ONLY).ordinal();
    }

    @Override
    public ServerboundMovePlayer read(ByteBuf input, ProtocolContext context) {
        Variant variant = VARIANTS[context.variant()];
        boolean position = variant.position;
        boolean rotation = variant.rotation;
        double x = position ? input.readDouble() : 0;
        double y = position ? input.readDouble() : 0;
        double z = position ? input.readDouble() : 0;
        float yaw = rotation ? input.readFloat() : 0;
        float pitch = rotation ? input.readFloat() : 0;
        int flags = input.readUnsignedByte();
        return new ServerboundMovePlayer(x, y, z, yaw, pitch, (flags & 1) != 0, (flags & 2) != 0, position, rotation);
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ServerboundMovePlayer packet) {
        if (packet.hasPosition()) {
            output.writeDouble(packet.x()).writeDouble(packet.y()).writeDouble(packet.z());
        }
        if (packet.hasRotation()) {
            output.writeFloat(packet.yaw()).writeFloat(packet.pitch());
        }
        output.writeByte((packet.onGround() ? 1 : 0) | (packet.horizontalCollision() ? 2 : 0));
    }
}
