package ac.cult.cultac.network;

import io.netty.util.concurrent.EventExecutor;

/** The executor that owns a connection's packets, and the Bedrock bridge session that requires it, if any. */
public record PacketOwner(EventExecutor executor, Object bedrockBridge) {
    public PacketOwner {
        java.util.Objects.requireNonNull(executor);
    }
}
