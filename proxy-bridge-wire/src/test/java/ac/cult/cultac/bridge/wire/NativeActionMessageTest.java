package ac.cult.cultac.bridge.wire;

import org.junit.Test;
import java.util.*;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class NativeActionMessageTest {
    @Test public void namedComponentsPreserveNbtNumericTypesAndRemoval() {
        var tree = new LinkedHashMap<String, Object>();
        tree.put("byte", (byte) 1); tree.put("short", (short) 2); tree.put("int", 3); tree.put("long", 4L);
        tree.put("float", 0.5F); tree.put("double", 0.25D); tree.put("removed", null);
        tree.put("list", List.of("minecraft:poison", 3)); tree.put("array", new int[]{1, 2});
        var decoded = (Map<?, ?>) NamedValueCodec.decode(NamedValueCodec.encode(tree));
        assertEquals(Byte.class, decoded.get("byte").getClass()); assertEquals(Short.class, decoded.get("short").getClass());
        assertEquals(Integer.class, decoded.get("int").getClass()); assertEquals(Long.class, decoded.get("long").getClass());
        assertEquals(Float.class, decoded.get("float").getClass()); assertEquals(Double.class, decoded.get("double").getClass());
        assertTrue(decoded.containsKey("removed")); assertNull(decoded.get("removed"));
        assertArrayEquals(new int[]{1, 2}, (int[]) decoded.get("array"));
    }
    @Test public void largeBookComponentsFragmentAndAssembleExactly() {
        String page = "Book page content ".repeat(500);
        var pages = Collections.nCopies(100, page);
        var component = new InventoryDeltaMessage.Component("minecraft:written_book_content", NamedValueCodec.encode(Map.of("pages", pages)));
        var item = new InventoryDeltaMessage.Item("minecraft:written_book", 1, List.of(component));
        var message = new InventoryDeltaMessage(List.of(new InventoryDeltaMessage.Slot(0, 36, item)), false, null);
        var fragments = new ArrayList<byte[]>(); InventoryFragments.emit(message.encode(), fragments::add);
        assertTrue(fragments.size() > 2); var assembly = new InventoryFragments(); byte[] complete = null;
        for (int n = 0; n < fragments.size(); n++) {
            assertTrue(fragments.get(n).length <= BridgeEnvelope.MAX_BODY_BYTES);
            complete = assembly.accept(fragments.get(n));
            if (n != fragments.size() - 1) assertNull(complete);
        }
        assertFalse(assembly.incomplete()); assertArrayEquals(message.encode(), complete);
        assertEquals("minecraft:written_book", InventoryDeltaMessage.decode(complete).slots().getFirst().item().name());
    }
    @Test public void fragmentsRejectGapsReplaysAndChangedTotals() {
        var fragments = new ArrayList<byte[]>(); InventoryFragments.emit(new byte[60_000], fragments::add);
        var assembly = new InventoryFragments(); assembly.accept(fragments.getFirst());
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(fragments.getFirst()));
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(fragments.get(2)));
        byte[] changed = fragments.get(1).clone(); ByteBuffer.wrap(changed).putInt(60_001);
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(changed));
        assembly.clear(); assertFalse(assembly.incomplete());
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(fragments.get(1)));
    }
    @Test public void inventoryEmptySlotCursorAndBulkWindowsRoundTrip() {
        var message = new InventoryDeltaMessage(List.of(new InventoryDeltaMessage.Slot(0, 45, null), new InventoryDeltaMessage.Slot(5, 2, null)), true, null);
        assertEquals(message, InventoryDeltaMessage.decode(message.encode()));
        assertThrows(IllegalArgumentException.class, () -> new InventoryDeltaMessage(List.of(new InventoryDeltaMessage.Slot(0, 1, null), new InventoryDeltaMessage.Slot(0, 1, null)), false, null));
    }
    @Test public void malformedNamedValuesAndInventoryAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NamedValueCodec.decode(new byte[]{127}));
        assertThrows(IllegalArgumentException.class, () -> NamedValueCodec.decode(new byte[]{0, 1}));
        assertThrows(IllegalArgumentException.class, () -> NamedValueCodec.encode(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> InventoryDeltaMessage.decode(new byte[]{0, 0, 0, 0}));
        byte[] message = new InventoryDeltaMessage(List.of(), false, null).encode();
        assertThrows(IllegalArgumentException.class, () -> InventoryDeltaMessage.decode(Arrays.copyOf(message, message.length + 1)));
    }
    @Test public void layerCorrectionsKeepBothNamedStatesAndOrder() {
        var message = new BlockUpdatesMessage(List.of(new BlockUpdatesMessage.Update(5, 65, 7, 0, "minecraft:oak_slab[type=bottom,waterlogged=false]"), new BlockUpdatesMessage.Update(5, 65, 7, 1, "minecraft:water[level=0]")));
        assertEquals(message, BlockUpdatesMessage.decode(message.encode()));
        assertThrows(IllegalArgumentException.class, () -> new BlockUpdatesMessage.Update(0, 0, 0, 2, "minecraft:air"));
        assertThrows(IllegalArgumentException.class, () -> BlockUpdatesMessage.decode(Arrays.copyOf(message.encode(), message.encode().length - 1)));
    }
}
