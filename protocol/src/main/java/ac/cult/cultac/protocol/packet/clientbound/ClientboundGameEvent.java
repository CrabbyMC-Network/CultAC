package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.GameEventType;

public record ClientboundGameEvent(GameEventType event, float param) implements ClientboundPacket {}
