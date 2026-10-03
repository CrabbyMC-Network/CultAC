package ac.cult.cultac.protocol;

import java.util.List;

/** Stateful model-only conversion. Its generated control packets never represent client actions. */
public interface PacketProjection extends AutoCloseable {
    record Frame(PacketDirection direction, ConnectionPhase phase, byte[] bytes, boolean generated) {}

    /** Observe an actual client-facing frame and advance both sides of the private model. */
    List<Frame> toModel(PacketDirection direction, ConnectionPhase phase, byte[] bytes, boolean mirror);

    /** Convert a Cult-authored native-value packet into client-facing wire frames. */
    List<Frame> toWire(PacketDirection direction, ConnectionPhase phase, byte[] bytes);

    @Override
    void close();
}
