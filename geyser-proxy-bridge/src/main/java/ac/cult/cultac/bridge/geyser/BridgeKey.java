package ac.cult.cultac.bridge.geyser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** An operator-provisioned private key, never generated or printed by the extension. */
final class BridgeKey {
    private BridgeKey() { }
    static byte[] read(Path directory) throws IOException {
        Path file = directory.resolve("proxy-bridge.key");
        if (!Files.isRegularFile(file)) throw new IOException("Provision proxy-bridge.key before enabling the bridge");
        byte[] key;
        try { key = java.util.Base64.getDecoder().decode(Files.readString(file).trim()); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed proxy-bridge.key", malformed); }
        if (key.length != 32) throw new IOException("proxy-bridge.key must encode exactly 32 bytes");
        return key;
    }
}
