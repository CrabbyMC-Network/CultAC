package ac.cult.cultac.network.packet;

import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;

import java.nio.ByteBuffer;
import java.util.List;

/** Consumed values plus owned entry bytes, so untouched metadata never needs re-encoding. */
public record EntityMetadata(int id, List<Entry> packedItems) implements ClientboundPacket {
    public EntityMetadata { packedItems = List.copyOf(packedItems); }

    /** A null value means the payload was traversed but is not consumed by the anticheat. */
    public record Entry(int id, Object value, ByteBuffer bytes) {
        public Entry { bytes = bytes.asReadOnlyBuffer(); }
        @Override public ByteBuffer bytes() { return bytes.asReadOnlyBuffer(); }

        /** The only authored metadata value: the existing health spoof. Layout is pinned to 26.3. */
        public static Entry health(float value) {
            return new Entry(9, value, ByteBuffer.allocate(6).put((byte) 9).put((byte) 3).putFloat(value).flip());
        }
    }
}
