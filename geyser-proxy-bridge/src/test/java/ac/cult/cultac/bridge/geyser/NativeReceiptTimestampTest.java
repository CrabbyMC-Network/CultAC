package ac.cult.cultac.bridge.geyser;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeReceiptTimestampTest {
    @Test public void scaledNegativeWireEchoRoundTripsExactlyWithoutOverflow() {
        for (long counter : new long[]{1, 12345, NativeReceiptTimestamp.MAX_COUNTER}) {
            long original = NativeReceiptTimestamp.marker(counter);
            long nativeEcho = Math.multiplyExact(original, NativeReceiptTimestamp.SCALE);
            assertTrue(NativeReceiptTimestamp.reserved(nativeEcho));
            assertEquals(original, NativeReceiptTimestamp.original(nativeEcho));
        }
    }
    @Test public void ordinaryJavaAndMalformedReceiptIdsCannotEnterPrivateNamespace() {
        assertFalse(NativeReceiptTimestamp.reserved(-32767L * NativeReceiptTimestamp.SCALE));
        assertFalse(NativeReceiptTimestamp.reserved(NativeReceiptTimestamp.marker(1)));
        assertFalse(NativeReceiptTimestamp.reserved(NativeReceiptTimestamp.marker(1) * NativeReceiptTimestamp.SCALE + 1));
    }
    @Test public void pendingMarkerMatchesEveryKnownDeviceEchoScale() {
        long original = NativeReceiptTimestamp.marker(42);
        for (long scale : new long[]{NativeReceiptTimestamp.SCALE, 1_000L, 1L})
            assertEquals(original, NativeReceiptTimestamp.pendingOriginal(Math.multiplyExact(original, scale), value -> value == original));
    }
    @Test public void onlyStillPendingGatewayMarkersAreClaimed() {
        long original = NativeReceiptTimestamp.marker(42);
        assertEquals(NativeReceiptTimestamp.NONE,
                NativeReceiptTimestamp.pendingOriginal(original * NativeReceiptTimestamp.SCALE, value -> false));
        // Geyser's own echoes (Java ping ids, any scale) never fall in the gateway namespace.
        for (long geyser : new long[]{0, 1, -32767, -32767_000L, -32767_000_000L, 1_759_820_000_000L})
            assertEquals(NativeReceiptTimestamp.NONE, NativeReceiptTimestamp.pendingOriginal(geyser, value -> true));
        assertEquals(NativeReceiptTimestamp.NONE, NativeReceiptTimestamp.pendingOriginal(original * 1_000L + 1, value -> true));
    }
    @Test(expected = IllegalArgumentException.class) public void cannotWrapCounterAndReuseAnOldReceipt() {
        NativeReceiptTimestamp.marker(NativeReceiptTimestamp.MAX_COUNTER + 1);
    }
}
