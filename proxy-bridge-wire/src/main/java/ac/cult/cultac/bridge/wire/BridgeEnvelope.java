package ac.cult.cultac.bridge.wire;

import java.util.Objects;
import java.util.UUID;

/** Authenticated connection-scoped message; this transport does not grant movement permission. */
public record BridgeEnvelope(Direction direction, Kind kind, UUID player, UUID connection,
                             long sequence, byte[] body) {
    public static final int MAX_BODY_BYTES = 24 * 1024;
    public enum Direction { TO_BACKEND, TO_GATEWAY }
    public enum Kind { CHALLENGE, HELLO, CLIENT_PACKET, INPUT_RESULT, TELEPORT_EMISSION, LATENCY_RECEIPT,
                       SERVER_TELEPORT, SERVER_CORRECTION, ACTOR_CONTEXT, CLOSE, INVENTORY_DIFF }

    public BridgeEnvelope {
        Objects.requireNonNull(direction); Objects.requireNonNull(kind);
        Objects.requireNonNull(player); Objects.requireNonNull(connection); Objects.requireNonNull(body);
        if (sequence < 0 || body.length > MAX_BODY_BYTES) throw new IllegalArgumentException("Invalid bridge message bounds");
        body = body.clone();
    }

    @Override public byte[] body() { return body.clone(); }
}
