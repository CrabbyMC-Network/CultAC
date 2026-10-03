package ac.cult.cultac.bedrock.replay.offline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import ac.cult.cultac.checks.impl.badpackets.BadPacketsG;
import ac.cult.cultac.checks.impl.badpackets.BadPacketsM;
import ac.cult.cultac.events.packets.listeners.PacketPlayerRespawn;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.packet.ClientboundPackets;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundLogin;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundRespawn;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundSetHealth;
import ac.cult.cultac.protocol.value.GameMode;
import ac.cult.cultac.protocol.value.PlayerSpawnInfo;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.junit.Test;

public final class PacketPlayerRespawnLifecycleTest {
    @Test
    public void loginSeedsClientVisibleDeathScreenOption() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try (var server = OfflineCultTestBootstrap.withServerRegistries(OfflineCultTestBootstrap.vanillaRegistries())) {
            PacketPlayerRespawn listener = new PacketPlayerRespawn();
            BadPacketsM badPacketsM = player.checkManager.getListener(BadPacketsM.class);

            ClientboundLogin hiddenDeathScreen = loginPacket(false);
            listener.onLogin(sendEvent(player, ClientboundPackets.LOGIN, hiddenDeathScreen), player, hiddenDeathScreen);
            assertFalse(player.packetStateData.showsDeathScreen);
            badPacketsM.onDeath();
            assertFalse(booleanField(badPacketsM, "menu"));

            ClientboundLogin visibleDeathScreen = loginPacket(true);
            listener.onLogin(
                    sendEvent(player, ClientboundPackets.LOGIN, visibleDeathScreen), player, visibleDeathScreen);
            assertTrue(player.packetStateData.showsDeathScreen);
            badPacketsM.onDeath();
            assertTrue(booleanField(badPacketsM, "menu"));
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    @Test
    public void repeatedLethalHealthPacketsReopenBadPacketsMDeathState() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try (var server = OfflineCultTestBootstrap.withServerRegistries(OfflineCultTestBootstrap.vanillaRegistries())) {
            PacketPlayerRespawn listener = new PacketPlayerRespawn();
            ClientboundSetHealth lethal = new ClientboundSetHealth(0.0F, 20, 5.0F);

            listener.onSetHealth(sendEvent(player, ClientboundPackets.SET_HEALTH, lethal), player, lethal);
            assertTrue(player.compensatedEntities.getSelf().isDead);
            assertTrue(booleanField(player.checkManager.getListener(BadPacketsM.class), "menu"));

            player.checkManager.getListener(BadPacketsM.class).onRespawn();
            assertFalse(booleanField(player.checkManager.getListener(BadPacketsM.class), "menu"));

            // The native server is newer than 1.9, so the old PacketEvents listener did not
            // suppress an identical health packet. It must reapply death-screen state.
            listener.onSetHealth(sendEvent(player, ClientboundPackets.SET_HEALTH, lethal), player, lethal);
            assertTrue(booleanField(player.checkManager.getListener(BadPacketsM.class), "menu"));
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    @Test
    public void respawnCallbacksRunOnlyWhenTheRespawnTransactionIsAcknowledged() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try (var server = OfflineCultTestBootstrap.withServerRegistries(OfflineCultTestBootstrap.vanillaRegistries())) {
            PacketPlayerRespawn listener = new PacketPlayerRespawn();
            BadPacketsM badPacketsM = player.checkManager.getListener(BadPacketsM.class);
            BadPacketsG badPacketsG = player.checkManager.getListener(BadPacketsG.class);
            badPacketsM.onDeath();

            ClientboundRespawn respawn = respawnPacket();
            listener.onRespawn(sendEvent(player, ClientboundPackets.RESPAWN, respawn), player, respawn);

            assertTrue(booleanField(badPacketsM, "menu"));
            assertFalse(booleanField(badPacketsG, "respawn"));

            player.lastTransactionReceived.set(1);
            player.latencyUtils.handleNettySyncTransaction(1);

            assertFalse(booleanField(badPacketsM, "menu"));
            assertTrue(booleanField(badPacketsG, "respawn"));
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    private static ClientboundRespawn respawnPacket() {
        var registry = OfflineCultTestBootstrap.vanillaRegistries().lookupOrThrow(Registries.DIMENSION_TYPE);
        int id = registry.getId(
                registry.getOrThrow(BuiltinDimensionTypes.OVERWORLD).value());
        return new ClientboundRespawn(new PlayerSpawnInfo(id, "minecraft:overworld", GameMode.SURVIVAL));
    }

    private static ClientboundLogin loginPacket(boolean showDeathScreen) {
        return new ClientboundLogin(1, showDeathScreen, respawnPacket().spawnInfo());
    }

    private static <R extends ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket>
            PacketSendEvent<R> sendEvent(CultPlayer player, PacketType<R> type, R packet) {
        return new PacketSendEvent<>(player.user, ConnectionPhase.PLAY, type, packet, false);
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static CultPlayer offlineJavaPlayer() {
        UUID playerId = UUID.fromString("9c5e440b-265d-435f-98f1-1f539659c002");
        var platform = mock(ac.cult.cultac.network.PlatformConnection.class);
        when(platform.registries())
                .thenReturn(new ac.cult.cultac.utils.minecraft.MinecraftRegistries(
                        OfflineCultTestBootstrap::vanillaRegistries,
                        () -> net.minecraft.server.packs.resources.ResourceProvider.EMPTY));
        var connection = new ac.cult.cultac.network.CultConnection(
                platform,
                new EmbeddedChannel(),
                ac.cult.cultac.CultAPI.INSTANCE.getNetworkManager().dispatcher(),
                ignored -> null);
        for (var direction : ac.cult.cultac.protocol.PacketDirection.values())
            connection.phase(direction, ConnectionPhase.PLAY);
        User user = new User(new User.Profile(playerId, ".Respawn_Test"), connection);
        return new CultPlayer(user);
    }
}
