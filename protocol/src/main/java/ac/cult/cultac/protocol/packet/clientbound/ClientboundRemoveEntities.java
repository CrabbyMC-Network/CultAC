package ac.cult.cultac.protocol.packet.clientbound;

import java.util.List;

public record ClientboundRemoveEntities(List<Integer> entityIds) implements ClientboundPacket {
    public ClientboundRemoveEntities {
        entityIds = List.copyOf(entityIds);
    }
}
