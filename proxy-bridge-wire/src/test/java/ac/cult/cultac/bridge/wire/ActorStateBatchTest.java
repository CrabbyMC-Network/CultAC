package ac.cult.cultac.bridge.wire;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ActorStateBatchTest {
    private static ActorStateMessage motion(long request, long actor) {
        return new ActorStateMessage(request, actor, (int) actor, 0, ActorStateMessage.Kind.MOTION,
                new ActorStateMessage.Vector(new AuthInputMessage.Double3(actor, 0, -actor)).encode());
    }

    @Test public void batchRoundTripsEveryStateInOrder() {
        var states = new ArrayList<ActorStateMessage>();
        for (int n = 1; n <= ActorStateBatch.MAX_STATES; n++) states.add(motion(9, n));
        var decoded = ActorStateBatch.decode(new ActorStateBatch(9, states).encode());
        assertEquals(9, decoded.request());
        assertEquals(states.size(), decoded.states().size());
        for (int n = 0; n < states.size(); n++) {
            assertEquals(states.get(n).actorRuntimeId(), decoded.states().get(n).actorRuntimeId());
            assertArrayEquals(states.get(n).state(), decoded.states().get(n).state());
        }
    }

    @Test public void batchBoundsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new ActorStateBatch(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ActorStateBatch(1, List.of(motion(1, 1), motion(2, 2))));
        var tooMany = new ArrayList<ActorStateMessage>();
        for (int n = 0; n <= ActorStateBatch.MAX_STATES; n++) tooMany.add(motion(1, n + 1));
        assertThrows(IllegalArgumentException.class, () -> new ActorStateBatch(1, tooMany));
        var bytes = new ActorStateBatch(4, List.of(motion(4, 1))).encode();
        assertThrows(IllegalArgumentException.class, () -> ActorStateBatch.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IllegalArgumentException.class, () -> ActorStateBatch.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    }

    @Test public void protocolVersionMismatchIsRejectedUpFront() {
        var key = new byte[32];
        var codec = new BridgeEnvelopeCodec(key);
        var encoded = codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND, BridgeEnvelope.Kind.HELLO,
                UUID.randomUUID(), UUID.randomUUID(), 0, new byte[0]));
        encoded[4] = 1; // a version-1 peer
        // The HMAC no longer matches either, but a mismatched pair must never be accepted.
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
    }
}
