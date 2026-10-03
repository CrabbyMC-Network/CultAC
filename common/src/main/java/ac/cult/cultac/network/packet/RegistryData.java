package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.ResourceKey;

/** Only the block-transformer/provider definitions used by placement are decoded. */
public record RegistryData(
        ResourceKey<? extends Registry<?>> registry, List<RegistrySynchronization.PackedRegistryEntry> entries)
        implements ClientboundPacket {
    public RegistryData {
        entries = List.copyOf(entries);
    }
}
