package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.BridgeEnvelopeCodec;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.session.GeyserSession;

/** Runs inside the Geyser loader at the proxy, rather than pretending Geyser is on Bukkit. */
public final class CultProxyExtension implements Extension {
    private final Map<GeyserSession, GatewaySession> sessions = new IdentityHashMap<>();
    private BridgeEnvelopeCodec codec;
    private GatewayTranslators translators;

    @Subscribe public void initialize(GeyserPostInitializeEvent event) {
        try {
            BridgeEnvelopeCodec configured = new BridgeEnvelopeCodec(BridgeKey.read(dataFolder()));
            translators = new GatewayTranslators(this::session);
            // Sessions install only once every translator hook is in place.
            codec = configured;
            logger().info("Authenticated CultAC proxy bridge enabled; unbound backends retain Geyser behavior.");
        } catch (IOException | RuntimeException failure) {
            logger().severe("CultAC proxy bridge initialization failed", failure);
            disable();
        }
    }

    private synchronized GatewaySession session(GeyserSession session) { return sessions.get(session); }
    private synchronized void install(GeyserSession session) {
        if (codec == null) return;
        GatewaySession existing = sessions.get(session);
        if (existing != null) { existing.ensureInstalled(); return; }
        GatewaySession bridge = new GatewaySession(session, codec);
        sessions.put(session, bridge);
        bridge.ensureInstalled();
    }
    @Subscribe public void start(SessionInitializeEvent event) {
        if (event.connection() instanceof GeyserSession session) install(session);
    }
    @Subscribe public void join(SessionJoinEvent event) {
        if (event.connection() instanceof GeyserSession session) install(session);
    }
    @Subscribe public void login(SessionLoginEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            install(session);
            GatewaySession bridge = session(session);
            if (bridge != null) bridge.resetBackend();
        }
    }
    @Subscribe public synchronized void disconnect(SessionDisconnectEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            GatewaySession bridge = sessions.remove(session);
            if (bridge != null) bridge.close();
        }
    }
    @Subscribe public synchronized void shutdown(GeyserShutdownEvent event) {
        if (translators != null) translators.close();
        sessions.values().forEach(GatewaySession::close);
        sessions.clear();
    }
}
