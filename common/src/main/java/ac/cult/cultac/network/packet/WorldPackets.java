package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.state.BlockState;

/** Consumed world data; block values and section palettes still belong to vanilla. */
public final class WorldPackets {
    private WorldPackets() {}

    public record BlockUpdate(BlockPos position, BlockState state) implements ClientboundPacket {
        public BlockUpdate {
            position = position.immutable();
        }
    }

    public record SectionBlocksUpdate(List<BlockUpdate> updates) implements ClientboundPacket {
        public SectionBlocksUpdate {
            updates = List.copyOf(updates);
        }
    }

    /**
     * The decoder supplies an owned section byte array. Read-only views never retain the frame,
     * and each caller gets its own index. The consumer supplies its existing dimension's section count.
     */
    public record Chunk(
            int x, int z, ByteBuffer sections, List<BlockPos> tickerCandidates, ClientboundLightUpdatePacketData light)
            implements ClientboundPacket {
        public Chunk(int x, int z, ByteBuffer sections, List<BlockPos> tickerCandidates) {
            this(x, z, sections, tickerCandidates, null);
        }

        public Chunk {
            sections = sections.asReadOnlyBuffer();
            tickerCandidates = List.copyOf(tickerCandidates);
        }

        @Override
        public ByteBuffer sections() {
            return sections.asReadOnlyBuffer();
        }
    }

    public record LightUpdate(int x, int z, ClientboundLightUpdatePacketData light) implements ClientboundPacket {}
}
