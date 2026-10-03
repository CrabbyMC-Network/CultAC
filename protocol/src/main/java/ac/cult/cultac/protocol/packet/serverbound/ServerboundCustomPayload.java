package ac.cult.cultac.protocol.packet.serverbound;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** A plugin channel and its owned bytes, including brand payloads on Paper. */
public record ServerboundCustomPayload(String channel, byte[] data) implements ServerboundPacket {
    public ServerboundCustomPayload {
        Objects.requireNonNull(channel);
        data = Objects.requireNonNull(data).clone();
    }

    @Override public byte[] data() { return data.clone(); }

    @Override public boolean equals(Object other) {
        return other instanceof ServerboundCustomPayload payload
                && channel.equals(payload.channel) && Arrays.equals(data, payload.data);
    }

    @Override public int hashCode() { return 31 * channel.hashCode() + Arrays.hashCode(data); }

    @Override public String toString() {
        return "ServerboundCustomPayload[channel=" + channel + ", data=" + HexFormat.of().formatHex(data) + "]";
    }
}
