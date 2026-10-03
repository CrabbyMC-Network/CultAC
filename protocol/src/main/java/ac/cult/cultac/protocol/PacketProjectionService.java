package ac.cult.cultac.protocol;

import java.util.UUID;

/** Shared SPI; no ViaVersion or Minecraft classes cross the private loader boundary. */
public interface PacketProjectionService extends AutoCloseable {
    PacketProjection connection(ProtocolVersion wire, ProtocolVersion model, UUID id, String username);

    @Override
    void close();
}
