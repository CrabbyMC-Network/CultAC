package ac.cult.cultac.bridge.wire;

import java.io.*;
import java.util.*;

/** Client-only corrections produced when Geyser acknowledges Java block predictions. */
public record BlockUpdatesMessage(List<Update> updates) {
    public record Update(int x, int y, int z, int layer, String state) {
        public Update { if (layer != 0 && layer != 1 || state == null || state.length() > 2048) throw new IllegalArgumentException("Invalid native block correction"); }
    }
    public BlockUpdatesMessage { updates = List.copyOf(updates); if (updates.size() > 4096) throw new IllegalArgumentException("Large native block correction"); }
    public byte[] encode() {
        try { var b = new ByteArrayOutputStream(); var o = new DataOutputStream(b); o.writeInt(updates.size());
            for (var u : updates) { o.writeInt(u.x); o.writeInt(u.y); o.writeInt(u.z); o.writeByte(u.layer); o.writeUTF(u.state); }
            if (b.size() > BridgeEnvelope.MAX_BODY_BYTES - 40) throw new IllegalArgumentException("Large native block correction"); return b.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Invalid native block correction", e); }
    }
    public static BlockUpdatesMessage decode(byte[] b) {
        if (b.length > BridgeEnvelope.MAX_BODY_BYTES) throw new IllegalArgumentException("Large native block correction");
        try { var i = new DataInputStream(new ByteArrayInputStream(b)); int n = i.readInt();
            if (n < 0 || n > 4096) throw new IllegalArgumentException("Invalid native block correction count"); var updates = new ArrayList<Update>();
            for (int x = 0; x < n; x++) updates.add(new Update(i.readInt(), i.readInt(), i.readInt(), i.readUnsignedByte(), i.readUTF()));
            if (i.available() != 0) throw new IllegalArgumentException("Trailing native block correction"); return new BlockUpdatesMessage(updates);
        } catch (IOException e) { throw new IllegalArgumentException("Invalid native block correction", e); }
    }
}
