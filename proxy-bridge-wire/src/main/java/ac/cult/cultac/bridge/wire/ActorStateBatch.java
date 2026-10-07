package ac.cult.cultac.bridge.wire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Native state writes that reach the client together and share one receipt boundary. Every
 * state carries the batch request, and the backend applies them in order at that boundary.
 */
public record ActorStateBatch(long request, List<ActorStateMessage> states) {
    public static final int MAX_STATES = 64;
    /** Envelope body budget left for state entries after the batch header. */
    public static final int MAX_STATE_BYTES = BridgeEnvelope.MAX_BODY_BYTES - 12;

    public ActorStateBatch {
        if (request < 0 || states == null || states.isEmpty() || states.size() > MAX_STATES)
            throw new IllegalArgumentException("Invalid native state batch");
        states = List.copyOf(states);
        int bytes = 0;
        for (ActorStateMessage state : states) {
            if (state.request() != request) throw new IllegalArgumentException("Mixed native state batch");
            bytes += encodedSize(state);
        }
        if (bytes > MAX_STATE_BYTES) throw new IllegalArgumentException("Oversized native state batch");
    }

    /** Bytes one state occupies inside a batch, including its length prefix. */
    public static int encodedSize(ActorStateMessage state) { return 4 + state.encode().length; }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeLong(request); out.writeInt(states.size());
            for (ActorStateMessage state : states) {
                byte[] encoded = state.encode();
                out.writeInt(encoded.length); out.write(encoded);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static ActorStateBatch decode(byte[] bytes) {
        if (bytes == null || bytes.length > BridgeEnvelope.MAX_BODY_BYTES) throw new IllegalArgumentException("Invalid native state batch size");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            long request = in.readLong();
            int count = in.readInt();
            if (count < 1 || count > MAX_STATES) throw new IllegalArgumentException("Invalid native state batch count");
            List<ActorStateMessage> states = new ArrayList<>(count);
            for (int n = 0; n < count; n++) {
                int length = in.readInt();
                if (length < 1 || length > in.available()) throw new IllegalArgumentException("Invalid native state length");
                states.add(ActorStateMessage.decode(in.readNBytes(length)));
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing native state batch data");
            return new ActorStateBatch(request, states);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("Truncated native state batch", truncated);
        }
    }
}
