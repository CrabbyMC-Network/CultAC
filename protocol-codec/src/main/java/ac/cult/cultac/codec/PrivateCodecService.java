package ac.cult.cultac.codec;

import ac.cult.cultac.protocol.PacketProjection;
import ac.cult.cultac.protocol.PacketProjectionService;
import ac.cult.cultac.protocol.ProtocolVersion;
import com.viaversion.viaversion.ViaManagerImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Loaded in its own classloader; its Via singleton is never the platform plugin's singleton. */
public final class PrivateCodecService implements PacketProjectionService {
    private final ViaManagerImpl manager;
    private final Set<CodecConnection> sessions = new HashSet<>();
    private boolean closed;

    public PrivateCodecService(Path directory) throws Exception {
        Files.createDirectories(directory);
        manager = ModernProtocols.open(directory.toFile());
    }

    @Override
    public synchronized PacketProjection connection(
            ProtocolVersion wire, ProtocolVersion model, UUID id, String username) {
        if (closed) throw new IllegalStateException("Codec service is closed");
        if (wire.protocol() >= model.protocol()) throw new IllegalArgumentException("Expected an older wire version");
        var connection = new CodecConnection(wire, model, id, username, this::remove);
        sessions.add(connection);
        return connection;
    }

    private synchronized void remove(CodecConnection connection) {
        sessions.remove(connection);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (var session : Set.copyOf(sessions)) session.close();
        manager.destroy();
    }
}
