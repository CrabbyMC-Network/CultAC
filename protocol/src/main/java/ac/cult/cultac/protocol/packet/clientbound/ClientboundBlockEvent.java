package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.BlockPos;
import java.util.Objects;

/** The block type ID is a server registry ID, not a block-state or palette value. */
public record ClientboundBlockEvent(BlockPos position, int action, int parameter, int blockId) implements ClientboundPacket {
    public ClientboundBlockEvent { Objects.requireNonNull(position); }
}
