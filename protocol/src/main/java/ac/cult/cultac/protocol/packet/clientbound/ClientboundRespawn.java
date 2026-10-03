package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.PlayerSpawnInfo;
import java.util.Objects;

public record ClientboundRespawn(PlayerSpawnInfo spawnInfo) implements ClientboundPacket {
    public ClientboundRespawn {
        Objects.requireNonNull(spawnInfo);
    }
}
