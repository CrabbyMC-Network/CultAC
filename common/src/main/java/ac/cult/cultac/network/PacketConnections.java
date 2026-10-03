package ac.cult.cultac.network;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.utils.anticheat.LogUtil;
import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutor;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** One directory of attached sessions, with a secondary current-account index. */
final class PacketConnections {
    private final Map<Channel, CultConnection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, CultConnection> currentByUuid = new ConcurrentHashMap<>();
    private volatile UserLifecycleHooks hooks = UserLifecycleHooks.NONE;
    private final Field configurationProfileField = resolveConfigurationProfileField();
    private final Method profileIdMethod = resolveProfileMethod("id", "getId");
    private final Method profileNameMethod = resolveProfileMethod("name", "getName");

    void hooks(UserLifecycleHooks hooks) { this.hooks = java.util.Objects.requireNonNull(hooks); }
    CultConnection get(Channel channel) { return channel == null ? null : connections.get(channel); }
    List<CultConnection> snapshot() { return List.copyOf(connections.values()); }

    void attach(CultConnection session) {
        if (connections.putIfAbsent(session.channel(), session) != null) throw new IllegalStateException("Already attached");
        session.initializer(this::prepare);
        session.channel().closeFuture().addListener(ignored -> disconnect(session));
    }

    private void prepare(CultConnection session) {
        if (!session.channel().isActive() || session.disconnected()) return;
        var profile = connectionProfile(session.nativeConnection());
        if (profile == null || profile.uuid() == null) return;
        var user = authenticate(session, profile.uuid(), profile.name());
        if (user != null && profile.player() != null) completeLogin(user, profile.player(), profile.serverPlayer());
    }

    private User authenticate(CultConnection session, UUID uuid, String name) {
        if (!session.channel().isActive() || session.disconnected()) return null;
        if (session.user() != null) return session.user();
        User user = new User(new User.Profile(uuid, name == null ? uuid.toString() : name), session);
        try {
            hooks.onAuthenticated(user);
            if (!session.channel().isActive()) return null;
            // Configuration packets already belong to the authenticated player.
            promote(user);
            if (ac.cult.cultac.network.protocol.util.viaversion.ViaVersionUtil.isAvailable()) {
                ac.cult.cultac.network.packet.LegacyViaInputBridge.install(user);
            }
            return user;
        } catch (RuntimeException | Error failure) {
            finishDisconnect(session);
            throw failure;
        }
    }

    private void promote(User user) {
        CultAPI.INSTANCE.getPlayerDataManager().addUser(user);
        currentByUuid.put(user.getUUID(), user.getCultConnection());
    }

    User getUser(Player player) {
        var nativeConnection = player == null ? null : currentPlayerConnection(player);
        var session = nativeConnection == null ? null : get(nativeConnection.channel);
        User user = session == null ? null : session.user();
        return user != null && !session.disconnected() && player.getUniqueId().equals(user.getUUID()) ? user : null;
    }

    User getUser(UUID uuid) {
        var session = uuid == null ? null : currentByUuid.get(uuid);
        return session == null ? null : session.user();
    }

    void playerJoined(Player player) {
        var nativeConnection = currentPlayerConnection(player);
        var session = nativeConnection == null ? null : get(nativeConnection.channel);
        if (session == null) return;
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        session.execute(() -> {
            User user = authenticate(session, uuid, name);
            if (user != null && uuid.equals(user.getUUID())) completeLogin(user, player, serverPlayer);
        });
    }

    private void completeLogin(User user, Player player, ServerPlayer serverPlayer) {
        var session = user.getCultConnection();
        if (session.disconnected() || session.loginNotified || !isPlayerConnection(player, user.getConnection())) return;
        user.bind(player, serverPlayer);
        if (currentByUuid.get(user.getUUID()) != session) promote(user);
        var cultPlayer = session.player();
        if (cultPlayer != null) {
            cultPlayer.updateServerPlayerBinding(player, serverPlayer);
            cultPlayer.updatePermissions();
        }
        session.loginNotified = true;
        hooks.onLogin(user, player);
    }

    CompletionStage<Void> disconnect(CultConnection session) {
        return afterPackets(session.owner(), () -> finishDisconnect(session));
    }

    private void finishDisconnect(CultConnection session) {
        if (!session.markDisconnected()) return;
        var user = session.user();
        try {
            if (user != null) {
                currentByUuid.remove(user.getUUID(), session);
                try { CultAPI.INSTANCE.getPlayerDataManager().onDisconnect(user); }
                finally { CultNetworkManager.clearChannelState(user); }
            }
        } finally {
            // Shutdown must still find a session while owner cleanup is in progress.
            connections.remove(session.channel(), session);
        }
    }

    CompletionStage<Void> disconnectRemainingUsers() {
        return CompletableFuture.allOf(snapshot().stream().map(session -> disconnect(session).toCompletableFuture())
                .toArray(CompletableFuture[]::new));
    }

    void clearUsers() {
        connections.clear();
        currentByUuid.clear();
        hooks = UserLifecycleHooks.NONE;
    }

    private static CompletionStage<Void> afterPackets(EventExecutor owner, Runnable cleanup) {
        var completion = new CompletableFuture<Void>();
        Runnable task = () -> {
            try { cleanup.run(); completion.complete(null); }
            catch (Throwable failure) { completion.completeExceptionally(failure); }
        };
        try {
            // Even a hook which closes its own channel finishes creation before teardown.
            owner.execute(task);
        } catch (RuntimeException rejected) {
            owner.terminationFuture().addListener(done -> task.run());
        }
        return completion.minimalCompletionStage();
    }

    private static Connection currentPlayerConnection(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return null;
        }
        if (craftPlayer.getHandle().connection == null) {
            return null;
        }
        return craftPlayer.getHandle().connection.connection;
    }

    private static boolean isPlayerConnection(Player player, Connection connection) {
        return currentPlayerConnection(player) == connection;
    }

    private ConnectionProfile connectionProfile(Connection connection) {
        if (connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener && listener.player != null) {
            ServerPlayer serverPlayer = listener.player;
            Player player = serverPlayer.getBukkitEntity();
            return new ConnectionProfile(
                    serverPlayer.getUUID(),
                    player.getName(),
                    player,
                    serverPlayer
            );
        }

        if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl configurationListener) {
            try {
                Object profile = configurationProfileField.get(configurationListener);
                if (profile == null) {
                    return null;
                }
                UUID uuid = (UUID) profileIdMethod.invoke(profile);
                String name = (String) profileNameMethod.invoke(profile);
                return new ConnectionProfile(uuid, name, null, null);
            } catch (ReflectiveOperationException exception) {
                LogUtil.warn("Failed to read configuration profile");
                exception.printStackTrace();
            }
        }

        return null;
    }

    private static Field resolveConfigurationProfileField() {
        try {
            Field field = ServerConfigurationPacketListenerImpl.class.getDeclaredField("gameProfile");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Failed to resolve Minecraft configuration profile field", exception);
        }
    }

    private static Method resolveProfileMethod(String... candidates) {
        for (String candidate : candidates) {
            try {
                return com.mojang.authlib.GameProfile.class.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // Authlib changed GameProfile from accessors to record-style methods.
            }
        }
        throw new IllegalStateException("Failed to resolve Authlib game profile accessor");
    }

    private record ConnectionProfile(UUID uuid, String name, Player player, ServerPlayer serverPlayer) {
    }

}
