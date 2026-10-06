package ac.cult.cultac.bridge.geyser;

/** Bedrock echoes the original server timestamp multiplied by one million. */
final class NativeReceiptTimestamp {
    static final long BASE = 4_000_000_000L, MAX_COUNTER = 0xffff_ffffL, SCALE = 1_000_000L;
    private NativeReceiptTimestamp() { }
    static long marker(long counter) {
        if (counter < 1 || counter > MAX_COUNTER) throw new IllegalArgumentException("Native receipt sequence exhausted");
        return -(BASE + counter);
    }
    static boolean reserved(long wireEcho) {
        if (wireEcho % SCALE != 0) return false;
        long original = wireEcho / SCALE;
        return original < -BASE && original >= -(BASE + MAX_COUNTER);
    }
    static long original(long wireEcho) {
        if (!reserved(wireEcho)) throw new IllegalArgumentException("Invalid native receipt echo");
        return wireEcho / SCALE;
    }
}
