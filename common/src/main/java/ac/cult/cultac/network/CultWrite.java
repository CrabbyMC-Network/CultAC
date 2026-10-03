package ac.cult.cultac.network;

import java.util.Objects;

/** A record to encode in stream order, optionally bypassing send handlers. */
public record CultWrite(Object packet, boolean silent) {
    public CultWrite { Objects.requireNonNull(packet); }
}
