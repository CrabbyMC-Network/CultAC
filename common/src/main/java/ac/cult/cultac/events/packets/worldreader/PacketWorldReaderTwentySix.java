package ac.cult.cultac.events.packets.worldreader;

import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.network.packet.WorldPackets.Chunk;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.latency.CompensatedGeysers;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedChunk;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedSection;
import ac.cult.cultac.utils.minecraft.NativeChunkSections;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Native section reader, using the host's original palette implementation. */
public class PacketWorldReaderTwentySix extends BasePacketWorldReader {

    @Override
    public void handleMapChunk(CultPlayer player, PacketSendEvent<Chunk> event, Chunk packet) {
        var dimension = player.compensatedWorld.getLastClientboundDimension();
        CachedSection[] chunks = new CachedSection[dimension.sectionCount()];
        var registries = player.user.registries().access();
        var bytes = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.sections()));
        try {
            for (int i = 0; i < chunks.length; i++) {
                var section = NativeChunkSections.create(registries);
                section.read(bytes);
                chunks[i] = new CachedSection(section.getStates().copy());
            }
        } finally {
            bytes.release();
        }
        var tickers = packet.tickerCandidates().stream()
                .filter(position -> hasGeyserTicker(chunks, dimension.minHeight(), position))
                .toList();
        addChunkToCache(event, player, chunks, true, dimension.dimension(), packet.x(), packet.z(), tickers);
        if (packet.light() != null) {
            player.latencyUtils.addRealTimeTask(
                    player.lastTransactionSent.get(),
                    () -> player.compensatedWorld.applyLight(
                            dimension.dimension(), packet.x(), packet.z(), packet.light()));
        }
    }

    private static boolean hasGeyserTicker(CachedSection[] sections, int minHeight, BlockPos position) {
        int offsetY = position.getY() - minHeight;
        int sectionIndex = offsetY >> 4;
        if (offsetY < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) return false;
        return CompensatedGeysers.hasTicker(sections[sectionIndex].getState(
                CachedChunk.index(position.getX() & 0xF, offsetY & 0xF, position.getZ() & 0xF)));
    }
}
