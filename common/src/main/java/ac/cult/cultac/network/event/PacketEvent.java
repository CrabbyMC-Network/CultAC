package ac.cult.cultac.network.event;

import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.protocol.ConnectionLifecycle;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.packet.Opaque;
import org.bukkit.entity.Player;
import java.util.Objects;

public abstract class PacketEvent<R> {
    private final User user;
    private final ConnectionPhase phase;
    private final PacketType<R> type;
    private final R original;
    private final long timestamp = System.currentTimeMillis();
    private R packet;
    private boolean cancelled;

    protected PacketEvent(User user, ConnectionPhase phase, PacketType<R> type, R packet) {
        this.user = user;
        this.phase = Objects.requireNonNull(phase);
        this.type = Objects.requireNonNull(type);
        this.original = this.packet = type.recordClass().cast(Objects.requireNonNull(packet));
    }

    public User getUser() { return user; }
    public Player getPlayer() { return user == null ? null : user.getPlayer(); }
    public ConnectionPhase getPhase() { return phase; }
    public PacketType<R> getPacketType() { return type; }
    public R getPacket() { return packet; }
    public R getOriginalPacket() { return original; }
    public long getTimestamp() { return timestamp; }
    public boolean isCancelled() { return cancelled; }
    public boolean isReplaced() { return packet != original; }

    public void setCancelled(boolean cancelled) {
        if (cancelled) requireEditable();
        this.cancelled = cancelled;
    }
    public void replace(R replacement) {
        requireEditable();
        if (!type.writable()) throw new IllegalArgumentException("Read-only packet family: " + type);
        R checked = type.recordClass().cast(Objects.requireNonNull(replacement));
        if (checked instanceof Opaque opaque && opaque.type() != type) throw new IllegalArgumentException("Different opaque family");
        packet = checked;
    }
    private void requireEditable() {
        if (ConnectionLifecycle.handles(type)) throw new IllegalStateException("Protocol-switch packet cannot be cancelled or replaced: " + type);
    }
    /** Packet callback failures retain the original bytes, as before. */
    public void discardChanges() { packet = original; cancelled = false; }
}
