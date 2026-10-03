package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.ClientInformation;
import java.util.Objects;

public record ServerboundClientInformation(ClientInformation information) implements ServerboundPacket {
    public ServerboundClientInformation {
        Objects.requireNonNull(information);
    }
}
