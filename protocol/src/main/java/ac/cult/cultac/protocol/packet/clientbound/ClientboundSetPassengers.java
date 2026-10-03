package ac.cult.cultac.protocol.packet.clientbound;

import java.util.List;

public record ClientboundSetPassengers(int vehicleId, List<Integer> passengers) implements ClientboundPacket {
    public ClientboundSetPassengers { passengers = List.copyOf(passengers); }
}
