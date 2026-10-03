package ac.cult.cultac.network;

import ac.cult.cultac.platform.bukkit.BukkitPacketCodecs;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.ProtocolRuntime;
import ac.cult.cultac.protocol.data.ProtocolData;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

/** Production catalog, with vanilla's built-in registries for offline fixtures. */
public final class TestProtocolRuntime {
    private TestProtocolRuntime() {}

    public static ProtocolRuntime create(ProtocolData data) {
        return ProtocolRuntime.create(data, catalog());
    }

    public static List<PacketType<?>> catalog() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        return BukkitPacketCodecs.catalog(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
