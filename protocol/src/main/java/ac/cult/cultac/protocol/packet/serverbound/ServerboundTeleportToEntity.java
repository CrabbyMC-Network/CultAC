package ac.cult.cultac.protocol.packet.serverbound;

import java.util.UUID;

public record ServerboundTeleportToEntity(UUID target) implements ServerboundPacket { }
