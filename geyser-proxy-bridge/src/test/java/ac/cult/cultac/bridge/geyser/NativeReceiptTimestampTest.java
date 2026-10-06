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
    @Test(expected = IllegalArgumentException.class) public void cannotWrapCounterAndReuseAnOldReceipt() {
        NativeReceiptTimestamp.marker(NativeReceiptTimestamp.MAX_COUNTER + 1);
    }
}
