package ac.cult.cultac.protocol.value;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Owned bytes with value equality; neither the input array nor an accessor can mutate them.
 */
public record ByteArray(byte[] bytes) {
    public ByteArray {
        bytes = Objects.requireNonNull(bytes).clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public int size() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ByteArray value && Arrays.equals(bytes, value.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return HexFormat.of().formatHex(bytes);
    }
}
