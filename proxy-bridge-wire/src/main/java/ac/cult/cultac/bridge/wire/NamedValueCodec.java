package ac.cult.cultac.bridge.wire;

import java.io.*;
import java.util.*;

/** Bounded named component values, independent of either connection's numeric registries. */
public final class NamedValueCodec {
    private NamedValueCodec() { }
    public static byte[] encode(Object value) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            write(out, value, 0);
            if (bytes.size() > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Oversized component");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid component", e); }
    }
    public static Object decode(byte[] bytes) {
        if (bytes.length > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Oversized component");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes)); Object value = read(in, 0);
            if (in.available() != 0) throw new IllegalArgumentException("Trailing component bytes");
            return value;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid component", e); }
    }
    private static void depth(int depth) { if (depth > 512) throw new IllegalArgumentException("Deep component"); }
    private static int count(DataInputStream in) throws IOException {
        int n = in.readInt(); if (n < 0 || n > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Invalid component count"); return n;
    }
    private static void write(DataOutputStream o, Object v, int d) throws IOException {
        depth(d);
        if (v == null) { o.writeByte(0); return; }
        if (v instanceof Boolean b) { o.writeByte(1); o.writeBoolean(b); }
        else if (v instanceof Byte b) { o.writeByte(2); o.writeByte(b); }
        else if (v instanceof Short b) { o.writeByte(3); o.writeShort(b); }
        else if (v instanceof Integer b) { o.writeByte(4); o.writeInt(b); }
        else if (v instanceof Long b) { o.writeByte(5); o.writeLong(b); }
        else if (v instanceof Float b) { if (!Float.isFinite(b)) throw new IllegalArgumentException("Nonfinite component"); o.writeByte(6); o.writeFloat(b); }
        else if (v instanceof Double b) { if (!Double.isFinite(b)) throw new IllegalArgumentException("Nonfinite component"); o.writeByte(7); o.writeDouble(b); }
        else if (v instanceof String b) { limit(b.length()); o.writeByte(8); o.writeInt(b.length()); for (int n = 0; n < b.length(); n++) o.writeChar(b.charAt(n)); }
        else if (v instanceof byte[] b) { limit(b.length); o.writeByte(9); o.writeInt(b.length); o.write(b); }
        else if (v instanceof int[] b) { limit(b.length); o.writeByte(10); o.writeInt(b.length); for (int n : b) o.writeInt(n); }
        else if (v instanceof long[] b) { limit(b.length); o.writeByte(11); o.writeInt(b.length); for (long n : b) o.writeLong(n); }
        else if (v instanceof List<?> b) { limit(b.size()); o.writeByte(12); o.writeInt(b.size()); for (var n : b) write(o, n, d + 1); }
        else if (v instanceof Map<?, ?> b) { limit(b.size()); o.writeByte(13); o.writeInt(b.size()); for (var e : b.entrySet()) { if (!(e.getKey() instanceof String key)) throw new IllegalArgumentException("Unnamed component"); o.writeUTF(key); write(o, e.getValue(), d + 1); } }
        else throw new IllegalArgumentException("Unsupported component value " + v.getClass());
    }
    private static void limit(int n) { if (n > InventoryFragments.MAX_LOGICAL_BYTES) throw new IllegalArgumentException("Large component count"); }
    private static Object read(DataInputStream i, int d) throws IOException {
        depth(d);
        return switch (i.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> i.readBoolean(); case 2 -> i.readByte(); case 3 -> i.readShort(); case 4 -> i.readInt(); case 5 -> i.readLong();
            case 6 -> { float n = i.readFloat(); if (!Float.isFinite(n)) throw new IllegalArgumentException("Nonfinite component"); yield n; }
            case 7 -> { double n = i.readDouble(); if (!Double.isFinite(n)) throw new IllegalArgumentException("Nonfinite component"); yield n; }
            case 8 -> { int n = count(i); if (n > i.available() / 2) throw new EOFException(); char[] text = new char[n]; for (int x = 0; x < n; x++) text[x] = i.readChar(); yield new String(text); }
            case 9 -> { int n = count(i); byte[] b = i.readNBytes(n); if (b.length != n) throw new EOFException(); yield b; }
            case 10 -> { int[] b = new int[count(i)]; for (int n = 0; n < b.length; n++) b[n] = i.readInt(); yield b; }
            case 11 -> { long[] b = new long[count(i)]; for (int n = 0; n < b.length; n++) b[n] = i.readLong(); yield b; }
            case 12 -> { int n = count(i); var b = new ArrayList<>(); for (int x = 0; x < n; x++) b.add(read(i, d + 1)); yield Collections.unmodifiableList(b); }
            case 13 -> { int n = count(i); var b = new LinkedHashMap<String, Object>(); for (int x = 0; x < n; x++) { String key = i.readUTF(); if (b.containsKey(key)) throw new IllegalArgumentException("Duplicate component key"); b.put(key, read(i, d + 1)); } yield Collections.unmodifiableMap(b); }
            default -> throw new IllegalArgumentException("Unknown component type");
        };
    }
}

