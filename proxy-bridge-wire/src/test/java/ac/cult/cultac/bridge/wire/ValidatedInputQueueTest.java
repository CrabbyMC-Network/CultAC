package ac.cult.cultac.bridge.wire;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ValidatedInputQueueTest {
    @Test public void projectionPrecedesNextRequestEvenWhenItEnqueues() {
        List<String> events = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(4,
            value -> events.add("request:" + value), value -> events.add("release:" + value));
        String first = new String("first");
        queue.offer(first);
        queue.offer("second");
        queue.complete(first, value -> {
            events.add("project:" + value);
            queue.offer("third");
            events.add("projected:" + value);
        });
        assertEquals(List.of("request:first", "project:first", "projected:first", "release:first", "request:second"), events);
        queue.close();
        assertEquals(List.of("release:second", "release:third"), events.subList(5, 7));
    }

    @Test public void equalValueCannotAuthorizeDifferentPacket() {
        List<String> released = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(2, ignored -> {}, released::add);
        String original = new String("input");
        queue.offer(original);
        assertThrows(IllegalStateException.class, () -> queue.complete(new String("input"), ignored -> fail()));
        assertTrue(released.isEmpty());
        queue.close();
        assertEquals(List.of(original), released);
    }

    @Test public void boundedQueueAndClosedQueueAlwaysTakeOwnership() {
        List<String> released = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(1, ignored -> {}, released::add);
        queue.offer("first");
        assertThrows(IllegalStateException.class, () -> queue.offer("overflow"));
        queue.close();
        queue.close();
        assertThrows(IllegalStateException.class, () -> queue.offer("closed"));
        assertEquals(List.of("overflow", "first", "closed"), released);
    }

    @Test public void projectionFailureClosesAndReleasesEveryOwnedPacketOnce() {
        List<String> released = new ArrayList<>();
        List<String> requested = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(3, requested::add, released::add);
        String original = new String("first");
        queue.offer(original);
        queue.offer("second");
        assertThrows(IllegalArgumentException.class, () -> queue.complete(original, ignored -> { throw new IllegalArgumentException(); }));
        queue.close();
        assertEquals(List.of("first"), requested);
        assertEquals(List.of("second", "first"), released);
    }

    @Test public void failedRequestClosesPendingOwnership() {
        List<String> released = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(2,
            ignored -> { throw new IllegalArgumentException(); }, released::add);
        assertThrows(IllegalArgumentException.class, () -> queue.offer("first"));
        queue.close();
        assertEquals(List.of("first"), released);
    }

    @Test public void disconnectDuringProjectionDoesNotReleaseCurrentTwice() {
        List<String> released = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(2, ignored -> {}, released::add);
        String original = new String("first");
        queue.offer(original);
        queue.offer("second");
        queue.complete(original, ignored -> queue.close());
        assertEquals(List.of("second", "first"), released);
    }

    @Test public void releaseFailureStillDiscardsEveryQueuedPacket() {
        List<String> released = new ArrayList<>();
        ValidatedInputQueue<String> queue = new ValidatedInputQueue<>(3, ignored -> {}, value -> {
            released.add(value);
            if(value.equals("first"))throw new IllegalArgumentException();
        });
        queue.offer("first");
        queue.offer("second");
        assertThrows(IllegalArgumentException.class, queue::close);
        queue.close();
        assertEquals(List.of("first", "second"), released);
    }
}
