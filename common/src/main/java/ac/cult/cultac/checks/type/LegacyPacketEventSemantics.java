package ac.cult.cultac.checks.type;

import ac.cult.cultac.network.event.PacketReceiveEvent;

/** PacketEvents classifications that were part of legacy check behavior. */
public final class LegacyPacketEventSemantics {
    private LegacyPacketEventSemantics() {}

    /** Matches the old Check#isAsync classification for serverbound PLAY packets. */
    public static boolean isAsync(PacketReceiveEvent<?> event) {
        var type = event.getPacketType();
        return type == ac.cult.cultac.protocol.packet.ServerboundPackets.KEEP_ALIVE
                || type == ac.cult.cultac.protocol.packet.ServerboundPackets.CHUNK_BATCH_RECEIVED
                || type == ac.cult.cultac.protocol.packet.ServerboundPackets.RESOURCE_PACK;
    }
}
