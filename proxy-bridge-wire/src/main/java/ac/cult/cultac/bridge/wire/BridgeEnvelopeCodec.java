package ac.cult.cultac.bridge.wire;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/** Versioned bounded HMAC wire format, independent of Bukkit, Geyser and Minecraft classes. */
public final class BridgeEnvelopeCodec {
    public static final String CHANNEL = "cult:bedrock_bridge";
    private static final int MAGIC = 0x43504231;
    // 2: ACTOR_CONTEXT_BATCH. Gateway and backend must run the same protocol version.
    private static final int VERSION = 2;
    private static final int TAG_BYTES = 32;
    private static final int HEADER_BYTES = 4 + 1 + 1 + 1 + 16 + 16 + 8 + 4;
    private final byte[] key;

    public BridgeEnvelopeCodec(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("Bridge key must contain 32 bytes");
        this.key = key.clone();
    }

    public byte[] encode(BridgeEnvelope message) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeByte(VERSION);
            out.writeByte(message.direction().ordinal()); out.writeByte(message.kind().ordinal());
            uuid(out, message.player()); uuid(out, message.connection()); out.writeLong(message.sequence());
            byte[] body = message.body(); out.writeInt(body.length); out.write(body); out.flush();
            byte[] unsigned = bytes.toByteArray(); bytes.write(mac(unsigned));
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public BridgeEnvelope decode(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_BYTES + TAG_BYTES
                || bytes.length > HEADER_BYTES + BridgeEnvelope.MAX_BODY_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Invalid bridge message size");
        }
        byte[] unsigned = Arrays.copyOf(bytes, bytes.length - TAG_BYTES);
        byte[] tag = Arrays.copyOfRange(bytes, bytes.length - TAG_BYTES, bytes.length);
        if (!MessageDigest.isEqual(mac(unsigned), tag)) throw new IllegalArgumentException("Invalid bridge authentication");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(unsigned));
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported bridge protocol");
            int direction = in.readUnsignedByte(), kind = in.readUnsignedByte();
            if (direction >= BridgeEnvelope.Direction.values().length || kind >= BridgeEnvelope.Kind.values().length) {
                throw new IllegalArgumentException("Unknown bridge message type");
            }
            UUID player = uuid(in), connection = uuid(in); long sequence = in.readLong();
            int length = in.readInt();
            if (length < 0 || length > BridgeEnvelope.MAX_BODY_BYTES || length != in.available()) {
                throw new IllegalArgumentException("Invalid bridge body size");
            }
            return new BridgeEnvelope(BridgeEnvelope.Direction.values()[direction], BridgeEnvelope.Kind.values()[kind],
                    player, connection, sequence, in.readNBytes(length));
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Truncated bridge message", malformed);
        }
    }

    private byte[] mac(byte[] bytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.key, "HmacSHA256"));
            return mac.doFinal(bytes);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HMAC unavailable", unavailable);
        }
    }
    private static void uuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits()); out.writeLong(uuid.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
