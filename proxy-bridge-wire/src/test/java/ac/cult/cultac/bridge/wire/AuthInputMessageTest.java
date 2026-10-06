package ac.cult.cultac.bridge.wire;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class AuthInputMessageTest {
    private AuthInputMessage input(List<AuthInputMessage.BlockAction> actions) {
        return new AuthInputMessage(975, 123, 1, 2, 0,
            new AuthInputMessage.Float3(100, 81.62001f, -20),
            new AuthInputMessage.Double3(100, 80, -20),
            new AuthInputMessage.Float3(0.125f, -0.0784f, 0), 45, 10, 45,
            new AuthInputMessage.Float2(1, 0), null, new AuthInputMessage.Float2(0.5f, 0),
            1L << 63, 5, 20, 7, new AuthInputMessage.Float2(15, 30), actions);
    }

    @Test public void roundTripPreservesCanonicalFeetOptionalVectorsFlagsAndActions() {
        AuthInputMessage value = input(List.of(new AuthInputMessage.BlockAction(3, 100, 79, -20, 1)));
        assertEquals(value, AuthInputMessage.decode(value.encode()));
    }

    @Test public void actionListIsCopiedAndBounded() {
        var actions = new java.util.ArrayList<AuthInputMessage.BlockAction>();
        actions.add(new AuthInputMessage.BlockAction(1, 2, 3, 4, 5));
        AuthInputMessage value = input(actions);
        actions.clear();
        assertEquals(1, value.blockActions().size());
        assertThrows(UnsupportedOperationException.class, () -> value.blockActions().clear());
        assertThrows(IllegalArgumentException.class, () -> input(java.util.Collections.nCopies(257,
            new AuthInputMessage.BlockAction(1, 2, 3, 4, 5))));
    }

    @Test public void truncatedAndTrailingPayloadsAreRejected() {
        byte[] bytes = input(List.of()).encode();
        for(int length = 0; length < bytes.length; length++) {
            byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(IllegalArgumentException.class, () -> AuthInputMessage.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> AuthInputMessage.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }

    @Test public void nonFiniteCoordinatesAreRejectedBeforeSimulation() {
        assertThrows(IllegalArgumentException.class, () -> new AuthInputMessage.Float3(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthInputMessage.Float2(0, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new AuthInputMessage.Double3(0, Double.NEGATIVE_INFINITY, 0));
    }
}
