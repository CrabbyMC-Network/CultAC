package ac.cult.cultac.bridge.geyser;

import java.util.function.LongPredicate;

/**
 * Gateway-owned NetworkStackLatency markers. Desktop/mobile Bedrock clients echo the server
 * timestamp multiplied by one million; other devices have historically echoed it unscaled or
 * scaled by one thousand (Geyser now ignores the value and relies on FIFO order). An echo is
 * only claimed when one of these forms maps exactly to a marker that is still pending.
 */
final class NativeReceiptTimestamp {
    static final long BASE = 4_000_000_000L, MAX_COUNTER = 0xffff_ffffL, SCALE = 1_000_000L;
    private static final long[] ECHO_SCALES = {SCALE, 1_000L, 1L};
    static final long NONE = 0L;
    private NativeReceiptTimestamp() { }
    static long marker(long counter) {
        if (counter < 1 || counter > MAX_COUNTER) throw new IllegalArgumentException("Native receipt sequence exhausted");
        return -(BASE + counter);
    }
    static boolean reserved(long wireEcho) {
        if (wireEcho % SCALE != 0) return false;
        return isMarker(wireEcho / SCALE);
    }
    static long original(long wireEcho) {
        if (!reserved(wireEcho)) throw new IllegalArgumentException("Invalid native receipt echo");
        return wireEcho / SCALE;
    }
    /** The pending marker this echo belongs to, or {@link #NONE} when it is not one of ours. */
    static long pendingOriginal(long wireEcho, LongPredicate pending) {
        for (long scale : ECHO_SCALES) {
            if (wireEcho % scale != 0) continue;
            long original = wireEcho / scale;
            if (isMarker(original) && pending.test(original)) return original;
        }
        return NONE;
    }
    private static boolean isMarker(long original) {
        return original < -BASE && original >= -(BASE + MAX_COUNTER);
    }
}
