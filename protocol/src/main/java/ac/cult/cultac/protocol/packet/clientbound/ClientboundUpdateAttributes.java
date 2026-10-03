package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.AttributeSnapshot;
import java.util.List;

public record ClientboundUpdateAttributes(int entityId, List<AttributeSnapshot> attributes)
        implements ClientboundPacket {
    public ClientboundUpdateAttributes {
        attributes = List.copyOf(attributes);
    }
}
