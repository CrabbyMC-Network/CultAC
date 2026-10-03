package ac.cult.cultac.network.protocol.player;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.network.protocol.util.FoliaCompatUtil;
import io.netty.util.concurrent.EventExecutor;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionStage;

public final class User {
    @Nullable
    private Player player;
    @Nullable
    private ServerPlayer handle;
    private final Profile profile;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    // Kept untyped so Java connections do not require the optional Geyser classes.
    private volatile Object bedrockBridgeConnection;
    private final ac.cult.cultac.network.CultConnection cultConnection;

    public User(Profile profile, ac.cult.cultac.network.CultConnection connection) {
        this.profile = java.util.Objects.requireNonNull(profile);
        this.cultConnection = java.util.Objects.requireNonNull(connection);
        connection.bind(this);
    }
    public ac.cult.cultac.network.CultConnection getCultConnection() { return cultConnection; }
    public ac.cult.cultac.player.CultPlayer getCultPlayer() { return cultConnection.player(); }

    public UUID getUUID() {
        return profile.getUUID();
    }

    public String getName() {
        return profile.getName();
    }

    public Profile getProfile() {
        return profile;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    @Nullable
    public ServerPlayer getHandle() {
        return handle;
    }

    public Connection getConnection() {
        return cultConnection.nativeConnection();
    }

    public Object getChannel() {
        return cultConnection.channel();
    }

    public EventExecutor getPacketExecutor() { return cultConnection.owner(); }
    public void execute(Runnable task) { if (getPacketExecutor().inEventLoop()) task.run(); else executeLater(task); }
    public void executeLater(Runnable task) { getPacketExecutor().execute(task); }

    @Nullable
    public Object getBedrockBridgeConnection() {
        return bedrockBridgeConnection;
    }

    public void setBedrockBridgeConnection(Object connection) {
        bedrockBridgeConnection = connection;
    }

    public ac.cult.cultac.protocol.ConnectionPhase getConnectionState() {
        return cultConnection.phase(ac.cult.cultac.protocol.PacketDirection.SERVERBOUND);
    }
    public ac.cult.cultac.protocol.ConnectionPhase getEncoderState() {
        return cultConnection.phase(ac.cult.cultac.protocol.PacketDirection.CLIENTBOUND);
    }
    public CompletionStage<Void> write(Object packet) {
        return completion(getCultConnection().write(packet instanceof ac.cult.cultac.network.CultWrite write ? write : new ac.cult.cultac.network.CultWrite(packet, false)));
    }
    public CompletionStage<Void> writeSilently(Object packet) {
        return completion(getCultConnection().write(new ac.cult.cultac.network.CultWrite(packet, true)));
    }
    public CompletionStage<Void> write(java.util.List<ac.cult.cultac.network.CultWrite> packets, boolean bundle) {
        return completion(getCultConnection().write(packets, bundle));
    }
    private static CompletionStage<Void> completion(io.netty.channel.ChannelFuture future) {
        var result = new java.util.concurrent.CompletableFuture<Void>();
        future.addListener(done -> { if (done.isSuccess()) result.complete(null); else result.completeExceptionally(done.cause()); });
        return result.minimalCompletionStage();
    }

    public void closeConnection() {
        if (!closeRequested.compareAndSet(false, true)) {
            return;
        }

        if (player != null) {
            FoliaCompatUtil.runTaskForEntity(player, CultAPI.INSTANCE.getPlugin(), () -> player.kick(Component.text("Disconnected")), null, 0);
        } else {
            getConnection().disconnect(net.minecraft.network.chat.Component.literal("Disconnected"));
        }
    }

    public void sendMessage(Component component) {
        if (player != null) {
            player.sendMessage(component);
        }
    }

    public void bind(@Nullable Player player, @Nullable ServerPlayer handle) {
        this.player = player;
        this.handle = handle;
    }

    public static final class Profile {
        private final UUID uuid;
        private final String name;

        public Profile(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getName() {
            return name;
        }
    }
}
