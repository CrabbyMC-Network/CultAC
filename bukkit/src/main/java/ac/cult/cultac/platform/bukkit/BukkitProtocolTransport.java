package ac.cult.cultac.platform.bukkit;

import ac.cult.cultac.network.CultNetworkManager;
import ac.cult.cultac.protocol.ProtocolRuntime;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ProtocolData;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import java.util.logging.Logger;

/** Resolves the codec catalog once before registering Paper's channel initializer. */
final class BukkitProtocolTransport {
    private BukkitProtocolTransport() { }
    static void initialize(CultNetworkManager manager, Logger logger) {
        var version = ProtocolVersion.of(SharedConstants.getProtocolVersion());
        var runtime = ProtocolRuntime.create(ProtocolData.load(version),
                BukkitPacketCodecs.catalog(net.minecraft.server.MinecraftServer.getServer().registryAccess()), commandInputLimit(version));
        var injector = new PaperInjector(manager);
        manager.configureTransport(runtime, injector::register, injector::unregister);
    }
    private static int commandInputLimit(ProtocolVersion version) {
        if (!version.atLeast(ProtocolVersion.V1_21_11)) return 32767;
        try { return (Integer) ServerboundChatCommandPacket.class.getField("MAX_CHAT_PACKET_INPUT_SIZE").get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native command input limit is unavailable", failure); }
    }
}
