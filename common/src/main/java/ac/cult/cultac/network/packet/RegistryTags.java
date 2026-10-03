package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

/** Owned configuration tag data for platforms that receive the model over the wire. */
public record RegistryTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags)
        implements ClientboundPacket {
    public RegistryTags {
        tags = Map.copyOf(tags);
    }
}
