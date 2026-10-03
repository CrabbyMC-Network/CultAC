package ac.cult.cultac.protocol;

import ac.cult.cultac.protocol.data.ProtocolData;

import java.util.Objects;

/** Immutable server data and wire identity shared across connections; contains no packet state. */
public record ProtocolContext(PacketType<?> type, ProtocolData data, ConnectionPhase phase,
                              String wireName, int commandInputLimit, int variant) {
    public ProtocolContext {
        Objects.requireNonNull(type);
        Objects.requireNonNull(data);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(wireName);
    }

    public PacketDirection direction() { return type.direction(); }
    public ProtocolVersion version() { return data.version(); }
}
