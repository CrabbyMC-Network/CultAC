package ac.cult.cultac.events.packets.worldreader;

import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.network.packet.WorldPackets.Chunk;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.latency.CompensatedGeysers;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedChunk;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedSection;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.server.MinecraftServer;

/** Pinned 26.3 section reader, using vanilla's palette implementation. */
public class PacketWorldReaderTwentySix extends BasePacketWorldReader {
    // Creating a vanilla palette factory builds DFU codecs; share it for this server registry.
    private static volatile SectionFactory sectionFactory;

    @Override
    public void handleMapChunk(CultPlayer player, PacketSendEvent<Chunk> event, Chunk packet) {
        var dimension = player.compensatedWorld.getLastClientboundDimension();
        CachedSection[] chunks = new CachedSection[dimension.sectionCount()];
        var palettes = palettes();
        var bytes = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.sections()));
        try {
            for (int i = 0; i < chunks.length; i++) {
                var section = new LevelChunkSection(palettes);
                section.read(bytes);
                chunks[i] = new CachedSection(section.getStates().copy());
            }
        } finally { bytes.release(); }
        var tickers = packet.tickerCandidates().stream()
                .filter(position -> hasGeyserTicker(chunks, dimension.minHeight(), position)).toList();
        addChunkToCache(event, player, chunks, true, dimension.dimension(), packet.x(), packet.z(), tickers);
    }

    private static boolean hasGeyserTicker(CachedSection[] sections, int minHeight, BlockPos position) {
        int offsetY = position.getY() - minHeight;
        int sectionIndex = offsetY >> 4;
        if (offsetY < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) return false;
        return CompensatedGeysers.hasTicker(sections[sectionIndex].getState(
                CachedChunk.index(position.getX() & 0xF, offsetY & 0xF, position.getZ() & 0xF)));
    }

    private static PalettedContainerFactory palettes() {
        RegistryAccess access = MinecraftServer.getServer().registryAccess();
        SectionFactory factory = sectionFactory;
        if (factory == null || factory.access() != access) {
            factory = new SectionFactory(access, PalettedContainerFactory.create(access));
            sectionFactory = factory;
        }
        return factory.palettes();
    }

    private record SectionFactory(RegistryAccess access, PalettedContainerFactory palettes) { }
}
