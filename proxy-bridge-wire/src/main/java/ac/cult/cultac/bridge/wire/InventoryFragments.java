package ac.cult.cultac.bridge.wire;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;

/** One contiguous logical inventory observation on the authenticated connection FIFO. */
public final class InventoryFragments {
    // Vanilla network NBT has a 2 MiB accounter; named tree tags/registry names add transport overhead.
    public static final int MAX_LOGICAL_BYTES = 4 * 1024 * 1024;
    private static final int CHUNK = BridgeEnvelope.MAX_BODY_BYTES - 8;
    private byte[] pending;
    private int offset;
    public boolean incomplete() { return pending != null; }
    public void clear() { pending = null; offset = 0; }
    public byte[] accept(byte[] fragment) {
        if (fragment.length < 9 || fragment.length > BridgeEnvelope.MAX_BODY_BYTES) throw new IllegalArgumentException("Invalid inventory fragment");
        var input = ByteBuffer.wrap(fragment); int total = input.getInt(), start = input.getInt();
        if (total < 1 || total > MAX_LOGICAL_BYTES || start != offset || start > total || input.remaining() > total - start)
            throw new IllegalArgumentException("Out of order inventory fragment");
        if (pending == null) pending = new byte[total];
        else if (pending.length != total) throw new IllegalArgumentException("Inventory fragment total changed");
        input.get(pending, offset, input.remaining()); offset += fragment.length - 8;
        if (offset != total) return null;
        byte[] value = pending; clear(); return value;
    }
    public static void emit(byte[] body, Consumer<byte[]> output) {
        if (body.length == 0 || body.length > MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Invalid inventory body");
        for (int offset = 0; offset < body.length; offset += CHUNK) {
            int length = Math.min(CHUNK, body.length - offset);
            var buffer = ByteBuffer.allocate(length + 8).putInt(body.length).putInt(offset);
            buffer.put(body, offset, length); output.accept(buffer.array());
        }
    }
}
