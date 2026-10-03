package ac.cult.cultac.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketDirection;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketConnectionsTest {
    private final PacketConnections connections = new PacketConnections();

    @BeforeEach
    void bootstrap() {
        OfflineCultTestBootstrap.installConfig();
    }

    private CultConnection session(UUID uuid) throws Exception {
        var channel = new EmbeddedChannel();
        var nativeConnection = mock(Connection.class);
        nativeConnection.channel = channel;
        var listener = mock(ServerConfigurationPacketListenerImpl.class);
        var profile = ServerConfigurationPacketListenerImpl.class.getDeclaredField("gameProfile");
        profile.setAccessible(true);
        profile.set(listener, new com.mojang.authlib.GameProfile(uuid, "ConnectionTest"));
        when(nativeConnection.getPacketListener()).thenReturn(listener);
        var session = new CultConnection(
                nativeConnection, channel, CultAPI.INSTANCE.getNetworkManager().dispatcher(), ignored -> null);
        session.phase(PacketDirection.SERVERBOUND, ConnectionPhase.CONFIGURATION);
        session.phase(PacketDirection.CLIENTBOUND, ConnectionPhase.CONFIGURATION);
        connections.attach(session);
        return session;
    }

    private static void close(CultConnection session) {
        var channel = (EmbeddedChannel) session.channel();
        channel.close();
        channel.runPendingTasks();
        channel.finishAndReleaseAll();
    }

    @Test
    void oldChannelClosePreservesReplacementAndItsExemption() throws Exception {
        var uuid = UUID.randomUUID();
        var first = session(uuid);
        var second = session(uuid);
        try {
            first.prepare();
            second.prepare();
            var players = CultAPI.INSTANCE.getPlayerDataManager();
            assertNotNull(first.player());
            assertNotNull(second.player());
            assertNotSame(first.player(), second.player());
            assertSame(second.user(), connections.getUser(uuid));
            players.exemptUser(second.user());
            close(first);
            assertTrue(first.disconnected());
            assertNull(first.player());
            assertNull(first.user().getCultPlayer());
            assertSame(second.user(), connections.getUser(uuid));
            assertSame(second.player(), players.getPlayer(second.user()));
            assertTrue(players.isExemptUser(second.user()));
            assertEquals(1, connections.snapshot().size());
        } finally {
            close(first);
            close(second);
        }
        assertTrue(connections.snapshot().isEmpty());
        assertNull(connections.getUser(uuid));
    }

    @Test
    void closingInsideAuthenticationCannotPublishAPartialPlayer() throws Exception {
        var calls = new AtomicInteger();
        connections.hooks(new UserLifecycleHooks() {
            @Override
            public void onAuthenticated(User user) {
                calls.incrementAndGet();
                assertNull(user.getCultPlayer());
                user.getCultConnection().channel().close();
            }
        });
        var session = session(UUID.randomUUID());
        try {
            session.prepare();
            ((EmbeddedChannel) session.channel()).runPendingTasks();
            assertEquals(1, calls.get());
            assertTrue(session.disconnected());
            assertNull(session.player());
            assertNull(connections.getUser(session.user().getUUID()));
            assertTrue(connections.snapshot().isEmpty());
        } finally {
            close(session);
        }
    }

    @Test
    void disconnectAndShutdownShareIdempotentOwnerCleanup() throws Exception {
        var session = session(UUID.randomUUID());
        try {
            session.prepare();
            var user = session.user();
            var player = session.player();
            var first = connections.disconnect(session).toCompletableFuture();
            var shutdown = connections.disconnectRemainingUsers().toCompletableFuture();
            ((EmbeddedChannel) session.channel()).runPendingTasks();
            first.join();
            shutdown.join();
            assertNull(user.getCultPlayer());
            assertFalse(CultAPI.INSTANCE.getPlayerDataManager().getEntries().contains(player));
            assertTrue(connections.snapshot().isEmpty());
            CultAPI.INSTANCE.getPlayerDataManager().addUser(user);
            assertNull(user.getCultPlayer(), "A disconnected session cannot be tracked again");
        } finally {
            close(session);
        }
    }

    @Test
    void removingPlayerTrackingLeavesTheConnectionAvailable() throws Exception {
        var session = session(UUID.randomUUID());
        try {
            session.prepare();
            var user = session.user();
            assertTrue(CultAPI.INSTANCE.getPlayerDataManager().remove(user));
            assertNull(session.player());
            assertNull(user.getCultPlayer());
            assertFalse(session.disconnected());
            assertSame(user, connections.getUser(user.getUUID()));
            assertSame(session, connections.get(session.channel()));
        } finally {
            close(session);
        }
    }
}
