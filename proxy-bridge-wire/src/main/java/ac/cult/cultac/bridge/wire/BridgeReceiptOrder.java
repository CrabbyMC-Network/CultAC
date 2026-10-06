package ac.cult.cultac.bridge.wire;

import java.util.Objects;
import java.util.UUID;

/** Receipt order belongs to one exact TCP connection, never just a reusable player UUID. */
public final class BridgeReceiptOrder {
    private final UUID player;
    private final UUID connection;
    private final BridgeEnvelope.Direction direction;
    private long nextSequence;
    private boolean closed;

    public BridgeReceiptOrder(UUID player, UUID connection, BridgeEnvelope.Direction direction, long firstSequence) {
        this.player = Objects.requireNonNull(player); this.connection = Objects.requireNonNull(connection);
        this.direction = Objects.requireNonNull(direction);
        if (firstSequence < 0) throw new IllegalArgumentException("Invalid sequence");
        this.nextSequence = firstSequence;
    }

    public synchronized boolean accept(BridgeEnvelope message) {
        if (closed || !player.equals(message.player()) || !connection.equals(message.connection())
                || direction != message.direction() || message.sequence() != nextSequence) return false;
        if (nextSequence == Long.MAX_VALUE) closed = true;
        else nextSequence++;
        return true;
    }

    public synchronized void close() { closed = true; }
}
