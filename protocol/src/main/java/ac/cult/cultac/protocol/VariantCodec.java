package ac.cult.cultac.protocol;

import java.util.List;

/**
 * One family carried by several wire names whose payloads the codec tells apart. The codec owns the
 * names, so their order and {@link ProtocolContext#variant()} cannot drift apart.
 */
public interface VariantCodec<R> extends PacketCodec<R> {
    /** Unnamespaced wire names; {@link ProtocolContext#variant()} indexes this list. */
    List<String> variants();

    /** The variant that carries {@code packet}; only writable families choose one. */
    default int variantOf(R packet) {
        throw new UnsupportedOnVersionException("Read-only codec " + getClass().getName());
    }
}
