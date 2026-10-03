package ac.cult.cultac.network;

import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.*;
import io.netty.channel.*;
import io.netty.util.concurrent.EventExecutor;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Authoritative identity, player attachment and transport state for one channel. */
public final class CultConnection {
    private final Channel channel;
    private final net.minecraft.network.Connection nativeConnection;
    boolean loginNotified;
    private volatile boolean disconnected;
    private final PacketDispatcher dispatcher;
    private final Function<Channel, EventExecutor> ownerResolver;
    private volatile EventExecutor owner;
    private volatile ConnectionPhase serverbound = ConnectionPhase.HANDSHAKE, clientbound = ConnectionPhase.HANDSHAKE;
    private volatile User user;
    private volatile CultPlayer player;
    private boolean ownerResolved, prepared, compressionRelocated;
    private Consumer<CultConnection> initializer = ignored -> {};
    private BiConsumer<Object, ChannelPromise> reentrantWriter;
    private final AtomicInteger work = new AtomicInteger();
    private Runnable removal;
    private volatile CompletableFuture<Void> removed;

    public CultConnection(Channel channel, PacketDispatcher dispatcher, Function<Channel, EventExecutor> resolver) {
        this(null, channel, dispatcher, resolver);
    }

    public CultConnection(
            net.minecraft.network.Connection nativeConnection,
            Channel channel,
            PacketDispatcher dispatcher,
            Function<Channel, EventExecutor> resolver) {
        this.nativeConnection = nativeConnection;
        this.channel = Objects.requireNonNull(channel);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        ownerResolver = Objects.requireNonNull(resolver);
        owner = channel.eventLoop();
    }

    public Channel channel() {
        return channel;
    }

    public net.minecraft.network.Connection nativeConnection() {
        return nativeConnection;
    }

    public boolean disconnected() {
        return disconnected;
    }

    synchronized boolean markDisconnected() {
        if (disconnected) return false;
        disconnected = true;
        return true;
    }

    public PacketDispatcher dispatcher() {
        return dispatcher;
    }

    public ProtocolRuntime runtime() {
        return dispatcher.runtime();
    }

    public EventExecutor owner() {
        return owner;
    }

    public User user() {
        return user;
    }

    public CultPlayer player() {
        return player;
    }

    public ConnectionPhase phase(PacketDirection direction) {
        return direction == PacketDirection.SERVERBOUND ? serverbound : clientbound;
    }

    public void phase(PacketDirection direction, ConnectionPhase phase) {
        if (direction == PacketDirection.SERVERBOUND) serverbound = Objects.requireNonNull(phase);
        else clientbound = Objects.requireNonNull(phase);
    }
    /** Called on I/O at configuration, or when reload attaches to an already-playing connection. */
    public void resolveOwner() {
        if (!ownerResolved && (serverbound == ConnectionPhase.CONFIGURATION || serverbound == ConnectionPhase.PLAY)) {
            ownerResolved = true;
            EventExecutor resolved = ownerResolver.apply(channel);
            if (resolved != null) owner = resolved;
        }
    }

    public void initializer(Consumer<CultConnection> initializer) {
        this.initializer = Objects.requireNonNull(initializer);
    }

    public void prepare() {
        if (!prepared && (serverbound == ConnectionPhase.CONFIGURATION || serverbound == ConnectionPhase.PLAY)) {
            prepared = true;
            initializer.accept(this);
        }
    }

    public synchronized void bind(User user) {
        if (user.getCultConnection() != this) throw new IllegalArgumentException("User belongs to another session");
        if (this.user != null && this.user != user) throw new IllegalStateException("Session already has an identity");
        if (disconnected) throw new IllegalStateException("Session has disconnected");
        this.user = user;
    }
    /** PlayerDataManager owns attachment mutations under this session's monitor. */
    public void player(CultPlayer player) {
        if (!Thread.holdsLock(this)) throw new IllegalStateException("Player attachment requires the session lock");
        if (player != null && (disconnected || player.user != user))
            throw new IllegalStateException("Invalid player attachment");
        this.player = player;
    }

    public void execute(Runnable task) {
        if (owner.inEventLoop()) task.run();
        else owner.execute(task);
    }

    public void executeLater(Runnable task) {
        owner.execute(task);
    }

    public void reentrantWriter(BiConsumer<Object, ChannelPromise> writer) {
        reentrantWriter = writer;
    }

    public ChannelFuture write(CultWrite message) {
        return writeMessage(message);
    }

    public ChannelFuture write(List<CultWrite> writes, boolean bundle) {
        return writeMessage(new WriteGroup(List.copyOf(writes), bundle));
    }

    private ChannelFuture writeMessage(Object message) {
        var promise = channel.newPromise();
        if (owner.inEventLoop() && reentrantWriter != null) reentrantWriter.accept(message, promise);
        else {
            Runnable send = () -> {
                if (removed != null) promise.tryFailure(new java.nio.channels.ClosedChannelException());
                else channel.writeAndFlush(message, promise);
            };
            if (channel.eventLoop().inEventLoop() && (owner == channel.eventLoop() || !owner.inEventLoop())) send.run();
            else channel.eventLoop().execute(send);
        }
        return promise;
    }

    public void forwarded(PacketType<?> type, Object packet) {
        serverbound = ConnectionLifecycle.nextPhase(PacketDirection.SERVERBOUND, serverbound, type, packet);
        clientbound = ConnectionLifecycle.nextPhase(PacketDirection.CLIENTBOUND, clientbound, type, packet);
    }

    public boolean relocateCompressionOnce() {
        if (compressionRelocated) return false;
        compressionRelocated = true;
        return true;
    }
    /** Work is accepted on I/O. A rejected return handoff may finish it on the owner. */
    public void beginWork() {
        work.incrementAndGet();
    }

    public void endWork() {
        if (work.decrementAndGet() != 0 || removed == null) return;
        Runnable finish = () -> {
            if (work.get() == 0 && removal != null) finishRemoval();
        };
        if (channel.eventLoop().inEventLoop()) finish.run();
        else {
            try {
                channel.eventLoop().execute(finish);
            } catch (RuntimeException rejected) {
                var completion = removed;
                if (completion != null) completion.completeExceptionally(rejected);
            }
        }
    }

    public java.util.concurrent.CompletionStage<Void> removeHandlers(Runnable remove) {
        if (removed != null) return removed;
        removed = new CompletableFuture<>();
        removal = remove;
        if (work.get() == 0) finishRemoval();
        return removed;
    }

    private void finishRemoval() {
        var action = removal;
        removal = null;
        try {
            action.run();
            removed.complete(null);
        } catch (Throwable failure) {
            removed.completeExceptionally(failure);
        }
    }

    public record WriteGroup(List<CultWrite> writes, boolean bundle) {}
}
