package ac.cult.cultac.bridge.wire;

import java.io.*;
import java.util.*;

/** Changes to Geyser's validated cache after projecting an original native action. */
public record InventoryDeltaMessage(List<Slot> slots, boolean cursorChanged, Item cursor) {
    public record Component(String name, boolean removed, byte[] value) {
        public Component(String name, byte[] value) { this(name, false, value); }
        public Component { Objects.requireNonNull(name); value = value.clone(); if (name.length() > 256 || value.length > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Invalid component"); }
        @Override public byte[] value() { return value.clone(); }
    }
    public record Item(String name, int amount, List<Component> components) {
        public Item { Objects.requireNonNull(name); components = List.copyOf(components); if (name.length() > 256 || amount < 1 || components.size() > 256) throw new IllegalArgumentException("Invalid named item"); }
    }
    public record Slot(int window, int index, Item item) {
        public Slot { if (window < 0 || index < 0 || index > 4095) throw new IllegalArgumentException("Invalid native slot"); }
    }
    public InventoryDeltaMessage {
        slots = List.copyOf(slots); if (slots.size() > 4096 || !cursorChanged && cursor != null) throw new IllegalArgumentException("Invalid inventory delta");
        var seen = new HashSet<Long>(); for (var s : slots) if (!seen.add(((long) s.window << 32) | s.index)) throw new IllegalArgumentException("Duplicate inventory slot");
    }
    public byte[] encode() {
        try { var b = new ByteArrayOutputStream(); var o = new DataOutputStream(b); o.writeInt(slots.size());
            for (var s : slots) { o.writeInt(s.window); o.writeInt(s.index); item(o, s.item); }
            o.writeBoolean(cursorChanged); if (cursorChanged) item(o, cursor);
            if (b.size() > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Large inventory delta"); return b.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid inventory delta", e); }
    }
    public static InventoryDeltaMessage decode(byte[] b) {
        if (b.length > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Large inventory delta");
        try { var i = new DataInputStream(new ByteArrayInputStream(b)); int n = count(i, 4096); var slots = new ArrayList<Slot>();
            for (int x = 0; x < n; x++) slots.add(new Slot(i.readInt(), i.readInt(), item(i)));
            boolean changed = i.readBoolean(); var result = new InventoryDeltaMessage(slots, changed, changed ? item(i) : null);
            if (i.available() != 0) throw new IllegalArgumentException("Trailing inventory delta"); return result;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid inventory delta", e); }
    }
    private static void item(DataOutputStream o, Item v) throws IOException {
        o.writeBoolean(v != null); if (v == null) return;
        o.writeUTF(v.name); o.writeInt(v.amount); o.writeInt(v.components.size());
        for (var c : v.components) { o.writeUTF(c.name); o.writeBoolean(c.removed); o.writeInt(c.value.length); o.write(c.value); }
    }
    private static Item item(DataInputStream i) throws IOException {
        if (!i.readBoolean()) return null; String name = i.readUTF(); int amount = i.readInt(), n = count(i, 256); var components = new ArrayList<Component>();
        for (int x = 0; x < n; x++) { String key = i.readUTF(); boolean removed = i.readBoolean(); int length = count(i, InventoryFragments.MAX_LOGICAL_BYTES); byte[] bytes = i.readNBytes(length); if (bytes.length != length) throw new EOFException(); components.add(new Component(key, removed, bytes)); }
        return new Item(name, amount, components);
    }
    private static int count(DataInputStream i, int max) throws IOException { int n = i.readInt(); if (n < 0 || n > max) throw new IllegalArgumentException("Invalid inventory count"); return n; }
}

