package ac.cult.cultac.protocol.packet;

import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket;

/** A family's shared value when Cult consumes its identity but none of its payload. */
public record Opaque(PacketType<Opaque> type) implements ServerboundPacket, ClientboundPacket {}
