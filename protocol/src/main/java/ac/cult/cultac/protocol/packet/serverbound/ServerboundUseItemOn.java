package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.BlockPos;
import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.value.Vec3d;
import java.util.Objects;

/** Cursor is relative to the clicked block, after native hit-location reconstruction. */
public record ServerboundUseItemOn(
        Hand hand,
        BlockPos blockPosition,
        Direction blockFace,
        Vec3d cursor,
        boolean insideBlock,
        boolean worldBorderHit,
        int sequence)
        implements ServerboundPacket {
    public ServerboundUseItemOn {
        Objects.requireNonNull(hand);
        Objects.requireNonNull(blockPosition);
        Objects.requireNonNull(blockFace);
        Objects.requireNonNull(cursor);
    }
}
