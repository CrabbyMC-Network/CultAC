package ac.cult.cultac.network;

import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.network.packet.LegacyViaInputBridge;
import ac.cult.cultac.network.protocol.util.viaversion.ViaVersionUtil;
import ac.cult.cultac.utils.anticheat.LogUtil;
import ac.cult.cultac.protocol.ProtocolRuntime;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CultNetworkManager implements Listener {
    private final PacketConnections connections = new PacketConnections();
    private PacketDispatcher dispatcher;
    private Runnable installChannels;
    private java.util.function.Supplier<CompletionStage<Void>> removeChannels;
    private java.util.function.Function<Channel, io.netty.util.concurrent.EventExecutor> ownerResolver = Channel::eventLoop;

    private JavaPlugin plugin;
    private boolean started;
    private CompletableFuture<Void> stopped;

    public PacketDispatcher dispatcher() { return java.util.Objects.requireNonNull(dispatcher, "Configure transport before use"); }

    public synchronized void configureTransport(ProtocolRuntime runtime, Runnable install,
            java.util.function.Supplier<CompletionStage<Void>> remove) {
        if (dispatcher != null) throw new IllegalStateException("Transport already configured");
        dispatcher = new PacketDispatcher(runtime);
        installChannels = java.util.Objects.requireNonNull(install);
        removeChannels = java.util.Objects.requireNonNull(remove);
    }

    public CultConnection createConnection(Connection nativeConnection, Channel channel) {
        var connection = new CultConnection(nativeConnection, channel, dispatcher(), ch -> ownerResolver.apply(ch));
        connections.attach(connection);
        return connection;
    }

    /**
     * Hooks new connections into the packet interceptor. Runs at plugin load so the
     * hook is in place before anything else snapshots the server's connection
     * initializer (Geyser captures it at enable time for its local channels).
     */
    public synchronized void load(JavaPlugin plugin) {
        if (stopped != null && !stopped.isDone()) throw new IllegalStateException("Network shutdown is still in progress");
        if (this.plugin != null) {
            return;
        }
        stopped = null;
        this.plugin = plugin;
        java.util.Objects.requireNonNull(installChannels, "Configure transport before load").run();
    }

    public void start(JavaPlugin plugin) {
        if (started) {
            return;
        }
        load(plugin);
        started = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Read-only snapshot; null means shutdown has not started. Does not initiate or retry shutdown. */
    public synchronized CompletionStage<Void> shutdownCompletion() {
        return stopped == null ? null : stopped.minimalCompletionStage();
    }

    public CompletionStage<Void> stop() {
        CompletableFuture<Void> completion;
        synchronized (this) {
            if (stopped != null && !stopped.isCompletedExceptionally()) return stopped.minimalCompletionStage();
            started = false;
            completion = stopped = new CompletableFuture<>();
        }
        try {
            HandlerList.unregisterAll(this);
            removeChannels.get()
                    .thenCompose(ignored -> connections.disconnectRemainingUsers())
                    .thenRun(this::finishStop)
                    .whenComplete((ignored, failure) -> {
                        if (failure == null) completion.complete(null);
                        else completion.completeExceptionally(failure);
                    });
        } catch (RuntimeException | Error failure) { completion.completeExceptionally(failure); }
        return completion.minimalCompletionStage();
    }

    private synchronized void finishStop() {
        connections.clearUsers();
        dispatcher.clear();
        plugin = null;
    }

    public void lifecycleHooks(UserLifecycleHooks hooks) { connections.hooks(hooks); }
    public CultConnection connection(Channel channel) { return connections.get(channel); }
    public List<CultConnection> connections() { return connections.snapshot(); }
    public CompletionStage<Void> disconnect(User user) { return connections.disconnect(user.getCultConnection()); }

    public User getUser(Player player) {
        return connections.getUser(player);
    }

    public User getUser(UUID uuid) {
        return connections.getUser(uuid);
    }

    public void setPacketOwnerResolver(java.util.function.Function<Channel, io.netty.util.concurrent.EventExecutor> resolver) {
        ownerResolver = java.util.Objects.requireNonNull(resolver);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        connections.playerJoined(event.getPlayer());
    }

    public static void runDeferredPacketTask(String phase, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            try {
                LogUtil.warn("Deferred packet task failed in " + phase + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            } catch (Throwable ignored) {
            }
            throwable.printStackTrace();
        }
    }

    public Object getChannel(UUID uuid) {
        User user = getUser(uuid);
        return user == null ? null : user.getChannel();
    }

    static void clearChannelState(User user) {
        if (ViaVersionUtil.isAvailable()) {
            LegacyViaInputBridge.remove(user);
        }
    }


}
