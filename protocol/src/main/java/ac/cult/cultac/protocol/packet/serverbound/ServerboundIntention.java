package ac.cult.cultac.protocol.packet.serverbound;

import ac.cult.cultac.protocol.value.ConnectionIntent;
import java.util.Objects;

public record ServerboundIntention(int protocolVersion, String hostName, int port, ConnectionIntent intention)
        implements ServerboundPacket {
    public ServerboundIntention {
        Objects.requireNonNull(hostName);
        Objects.requireNonNull(intention);
    }
}
