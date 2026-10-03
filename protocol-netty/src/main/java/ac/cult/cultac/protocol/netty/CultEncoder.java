package ac.cult.cultac.protocol.netty;

import ac.cult.cultac.network.CultConnection;
import ac.cult.cultac.network.CultNetworkManager;
import ac.cult.cultac.network.CultWrite;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.UnsupportedOnVersionException;
import ac.cult.cultac.protocol.packet.ClientboundPackets;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PromiseCombiner;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** I/O orders writes and flushes; the packet owner prepares one flat output at a time. */
public final class CultEncoder extends ChannelOutboundHandlerAdapter {
    public static final String NAME = "cult-encoder";

    private final CultConnection connection;
    private final ArrayDeque<Operation> queued = new ArrayDeque<>();
    private boolean busy;
    private boolean insideBundle;
    private volatile boolean removed;

    public CultEncoder(CultConnection connection) {
        this.connection = connection;
    }

    public static void install(CultConnection connection) {
        var pipeline = connection.channel().pipeline();
        String before = pipeline.get("encoder") != null ? "encoder" : "outbound_config";
        pipeline.addBefore(before, NAME, new CultEncoder(connection));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
        // Vanilla configuration tasks must run on I/O even while Cult is busy.
        if (!(message instanceof ByteBuf
                || message instanceof CultWrite
                || message instanceof CultConnection.WriteGroup
                || message instanceof CultConnection.WriteTask)) {
            ctx.write(message, promise);
            return;
        }
        if (!connection.packets().translated() && !busy && queued.isEmpty() && message instanceof ByteBuf frame) {
            try {
                var phase = connection.phase(PacketDirection.CLIENTBOUND);
                if (connection.dispatcher().get(PacketDirection.CLIENTBOUND, phase, Wire.peekVarInt(frame)) == null) {
                    ctx.write(frame, promise);
                    return;
                }
            } catch (Throwable failure) {
                frame.release();
                promise.tryFailure(failure);
                ctx.fireExceptionCaught(failure);
                return;
            }
        }
        accept(ctx, new Pending(message, promise));
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (!busy && queued.isEmpty()) ctx.flush();
        else accept(ctx, Flush.INSTANCE);
    }

    /** A host callback must follow already accepted packets, including their generated proofs. */
    public void executeAfterWrites(ChannelHandlerContext ctx, Runnable task) {
        if (ctx.handler() != this || !ctx.executor().inEventLoop()) {
            throw new IllegalStateException("Outbound callbacks must be queued on this channel's I/O executor");
        }
        accept(
                ctx,
                new Pending(new CultConnection.WriteTask(java.util.Objects.requireNonNull(task)), ctx.newPromise()));
    }

    private void accept(ChannelHandlerContext ctx, Operation operation) {
        connection.beginWork();
        queued.addLast(operation);
        drain(ctx);
    }

    private void drain(ChannelHandlerContext ctx) {
        if (busy) return;
        // Reentrant writes only enqueue, including while emitting or flushing on I/O.
        busy = true;
        try {
            while (!queued.isEmpty()) {
                Operation operation = queued.removeFirst();
                if (operation instanceof Pending pending) {
                    if (connection.owner() == ctx.executor()) {
                        dispatch(ctx, pending, false);
                    } else {
                        try {
                            connection.owner().execute(() -> dispatch(ctx, pending, true));
                            return; // Keep busy until the owner returns its output to I/O.
                        } catch (RuntimeException rejected) {
                            pending.discard(rejected);
                            complete(ctx, null, rejected);
                        }
                    }
                } else {
                    try {
                        if (!removed && ctx.channel().isActive()) ctx.flush();
                    } finally {
                        connection.endWork();
                    }
                }
            }
        } catch (Throwable failure) {
            busy = false;
            throw failure;
        }
        busy = false;
    }

    private void dispatch(ChannelHandlerContext ctx, Pending pending, boolean returnToIo) {
        Output output = null;
        Throwable failure = null;
        try {
            if (removed || !ctx.channel().isActive()) pending.discard(new ClosedChannelException());
            else output = process(ctx, pending);
        } catch (Throwable caught) {
            failure = caught;
        }
        if (!returnToIo) {
            complete(ctx, output, failure);
            return;
        }
        Output result = output;
        Throwable error = failure;
        try {
            ctx.executor().execute(() -> {
                try {
                    complete(ctx, result, error);
                } finally {
                    busy = false;
                    drain(ctx);
                }
            });
        } catch (RuntimeException rejected) {
            if (result != null) result.discard(rejected);
            try {
                ctx.channel().close();
            } finally {
                connection.endWork();
            }
        }
    }

    private void complete(ChannelHandlerContext ctx, Output output, Throwable failure) {
        try {
            if (failure != null) fail(ctx, failure);
            else if (output != null) emit(ctx, output);
        } catch (Throwable caught) {
            if (output != null) output.discard(caught);
            fail(ctx, caught);
        } finally {
            connection.endWork();
        }
    }

    private Output process(ChannelHandlerContext ctx, Pending pending) {
        var output = new Output();
        var activeFamilies = new ArrayDeque<PacketType<?>>();
        // Recursive callbacks finish first and append their complete groups here.
        // The enclosing group's frames have not been appended yet, so these writes
        // precede even its opening delimiter. No retained expansion tree is needed.
        connection.reentrantWriter(
                (message, promise) -> prepare(ctx, new Pending(message, promise), activeFamilies, output));
        try {
            prepare(ctx, pending, activeFamilies, output);
            return output;
        } catch (Throwable failure) {
            output.discard(failure);
            throw failure;
        } finally {
            connection.reentrantWriter(null);
        }
    }

    private void prepare(
            ChannelHandlerContext ctx, Pending pending, ArrayDeque<PacketType<?>> activeFamilies, Output output) {
        // Reentrant writes observe the emitted wire state, not an enclosing
        // group's planned delimiters. I/O cannot emit while this dispatch runs.
        var batch = new Batch(insideBundle);
        try {
            if (pending.message instanceof CultConnection.WriteTask task) {
                // The active writer captures callback-generated packets at this exact
                // queue position, before packets that arrived after the callback.
                task.action().run();
                output.flush |= !output.frames.isEmpty();
                pending.promise.trySuccess();
            } else if (pending.message instanceof CultConnection.WriteGroup group) {
                prepareGroup(ctx, group, pending.promise, activeFamilies, batch);
            } else {
                boolean requested = expandPacket(ctx, pending, activeFamilies, batch);
                if (!insideBundle && (requested || batch.output.frames.size() > 1)) wrap(ctx, batch.output);
            }
            output.append(batch.output);
        } catch (Throwable failure) {
            batch.output.discard(failure);
            pending.promise.tryFailure(failure);
            throw failure;
        }
    }

    private void prepareGroup(
            ChannelHandlerContext ctx,
            CultConnection.WriteGroup group,
            ChannelPromise promise,
            ArrayDeque<PacketType<?>> activeFamilies,
            Batch batch) {
        boolean wrapGroup = group.bundle() && !batch.inside;
        var parts = new LinkedHashSet<ChannelPromise>();
        if (wrapGroup) batch.add(delimiter(ctx));
        for (CultWrite write : group.writes()) {
            var part = ctx.newPromise();
            parts.add(part);
            var pending = new Pending(write, part);
            if (group.bundle()) {
                expandPacket(ctx, pending, activeFamilies, batch);
            } else {
                // Unbundled groups give each entry its own automatic bundle scope.
                var entry = new Batch(batch.inside);
                try {
                    boolean requested = expandPacket(ctx, pending, activeFamilies, entry);
                    if (!batch.inside && (requested || entry.output.frames.size() > 1)) wrap(ctx, entry.output);
                    batch.inside = entry.inside;
                    batch.output.append(entry.output);
                } catch (Throwable failure) {
                    entry.output.discard(failure);
                    throw failure;
                }
            }
        }
        if (wrapGroup) batch.add(delimiter(ctx));
        for (Frame frame : batch.output.frames) parts.add(frame.promise);
        batch.output.groups.add(new GroupCompletion(List.copyOf(parts), promise));
    }

    /** Expands only packets. The caller decides whether this expansion needs a bundle. */
    private boolean expandPacket(
            ChannelHandlerContext ctx, Pending pending, ArrayDeque<PacketType<?>> activeFamilies, Batch batch) {
        if (!(pending.message instanceof CultWrite write)) {
            return expandFrame(ctx, (ByteBuf) pending.message, pending.promise, false, true, activeFamilies, batch);
        }
        var phase = connection.phase(PacketDirection.CLIENTBOUND);
        ac.cult.cultac.protocol.ProjectedPackets.Encoded encoded;
        try {
            var type = connection.packets().writableType(write.packet());
            if (type.direction() != PacketDirection.CLIENTBOUND) {
                throw new UnsupportedOnVersionException("Not clientbound: " + type);
            }
            encoded = connection.packets().encode(phase, type, write.packet(), ctx.alloc(), connection);
        } catch (RuntimeException failure) {
            pending.promise.tryFailure(failure);
            return false;
        }
        if (encoded.frames().isEmpty()) {
            pending.promise.trySuccess();
            return false;
        }
        var parts = new ArrayList<ChannelPromise>();
        boolean requested = false;
        int transferred = 0;
        try {
            for (ByteBuf frame : encoded.frames()) {
                var part = encoded.frames().size() == 1 ? pending.promise : ctx.newPromise();
                parts.add(part);
                transferred++;
                requested |= expandFrame(
                        ctx, frame, part, write.silent(), !encoded.destinationObserved(), activeFamilies, batch);
            }
            if (encoded.frames().size() > 1)
                batch.output.groups.add(new GroupCompletion(List.copyOf(parts), pending.promise));
            return requested;
        } catch (Throwable failure) {
            for (int i = transferred; i < encoded.frames().size(); i++)
                encoded.frames().get(i).release();
            pending.promise.tryFailure(failure);
            throw failure;
        }
    }

    /** Owns one physical frame; model-derived values cannot replace its network bytes. */
    @SuppressWarnings("unchecked")
    private boolean expandFrame(
            ChannelHandlerContext ctx,
            ByteBuf bytes,
            ChannelPromise promise,
            boolean silent,
            boolean mirror,
            ArrayDeque<PacketType<?>> activeFamilies,
            Batch batch) {
        var phase = connection.phase(PacketDirection.CLIENTBOUND);
        boolean requested = false;
        PacketType<?> forwardedType = null;
        try {
            var values = connection
                    .packets()
                    .read(
                            phase,
                            PacketDirection.CLIENTBOUND,
                            bytes,
                            connection,
                            type -> connection.dispatcher().get(type) != null,
                            mirror);
            for (var value : values) {
                var route = connection.dispatcher().get(value.type());
                if (!value.derived()) forwardedType = route.type();
                connection.prepare();
                if (silent || route.send() == null || activeFamilies.contains(route.type())) continue;
                activeFamilies.addLast(route.type());
                try {
                    var type = (PacketType<ClientboundPacket>) route.type();
                    var event = new PacketSendEvent<>(
                            connection.user(), phase, type, (ClientboundPacket) value.packet(), batch.inside);
                    connection.dispatcher().send(event, route.send());
                    if (value.derived() && (event.isCancelled() || event.isReplaced()))
                        throw new IllegalStateException("Cannot mutate a derived model packet " + type);
                    if (event.isCancelled()) {
                        promise.trySuccess();
                        return false;
                    }
                    for (CultWrite write : event.writesBefore()) {
                        expandPacket(ctx, new Pending(write, ctx.newPromise()), activeFamilies, batch);
                    }
                    if (!value.derived()) {
                        if (bytes == null) throw new IllegalStateException("Multiple physical packet identities");
                        if (event.isReplaced()) {
                            var replacement = connection
                                    .packets()
                                    .encode(phase, type, event.getPacket(), ctx.alloc(), connection);
                            bytes.release();
                            bytes = null;
                            appendReplacement(ctx, replacement, promise, type, batch);
                        } else {
                            batch.add(new Frame(bytes, promise, type));
                            bytes = null;
                        }
                    }
                    for (CultWrite write : event.writesAfter()) {
                        expandPacket(ctx, new Pending(write, ctx.newPromise()), activeFamilies, batch);
                    }
                    batch.output.tasks.addAll(event.tasksAfter());
                    requested |= event.isBundleRequested();
                } finally {
                    activeFamilies.removeLast();
                }
            }
            // Unknown/cancelled model packets still retain their physical representation.
            if (bytes != null) {
                batch.add(new Frame(bytes, promise, forwardedType));
                bytes = null;
            }
            return requested;
        } catch (Throwable failure) {
            promise.tryFailure(failure);
            throw failure;
        } finally {
            if (bytes != null) bytes.release();
        }
    }

    private void appendReplacement(
            ChannelHandlerContext ctx,
            ac.cult.cultac.protocol.ProjectedPackets.Encoded replacement,
            ChannelPromise promise,
            PacketType<?> type,
            Batch batch) {
        if (replacement.frames().isEmpty()) {
            promise.trySuccess();
            return;
        }
        var parts = new ArrayList<ChannelPromise>();
        int transferred = 0;
        try {
            for (ByteBuf frame : replacement.frames()) {
                // Reverse conversion already advanced the destination endpoint. Keep
                // its paired endpoint current without dispatching the replacement twice.
                connection
                        .packets()
                        .read(
                                connection.phase(PacketDirection.CLIENTBOUND),
                                PacketDirection.CLIENTBOUND,
                                frame,
                                connection,
                                ignored -> false,
                                !replacement.destinationObserved());
                var part = replacement.frames().size() == 1 ? promise : ctx.newPromise();
                parts.add(part);
                batch.add(new Frame(frame, part, transferred == 0 ? type : null));
                transferred++;
            }
            if (replacement.frames().size() > 1)
                batch.output.groups.add(new GroupCompletion(List.copyOf(parts), promise));
        } catch (Throwable failure) {
            for (int i = transferred; i < replacement.frames().size(); i++)
                replacement.frames().get(i).release();
            throw failure;
        }
    }

    private void wrap(ChannelHandlerContext ctx, Output output) {
        output.frames.addFirst(delimiter(ctx));
        output.frames.addLast(delimiter(ctx));
    }

    private Frame delimiter(ChannelHandlerContext ctx) {
        var type = ClientboundPackets.BUNDLE_DELIMITER;
        var encoded = connection
                .packets()
                .encode(
                        connection.phase(PacketDirection.CLIENTBOUND),
                        type,
                        type.opaqueValue(),
                        ctx.alloc(),
                        connection);
        if (encoded.frames().size() != 1) {
            encoded.frames().forEach(ByteBuf::release);
            throw new IllegalStateException("Bundle delimiter must encode one physical frame");
        }
        ByteBuf bytes = encoded.frames().getFirst();
        connection
                .packets()
                .read(
                        connection.phase(PacketDirection.CLIENTBOUND),
                        PacketDirection.CLIENTBOUND,
                        bytes,
                        connection,
                        ignored -> false,
                        !encoded.destinationObserved());
        return new Frame(bytes, ctx.newPromise(), type);
    }

    private void emit(ChannelHandlerContext ctx, Output output) {
        if (removed || !ctx.channel().isActive()) {
            output.discard(new ClosedChannelException());
            return;
        }
        for (GroupCompletion group : output.groups) {
            var combiner = new PromiseCombiner(ctx.executor());
            for (ChannelPromise part : group.parts) combiner.add((Future<?>) part);
            combiner.finish(group.promise);
        }
        while (!output.frames.isEmpty()) {
            Frame frame = output.frames.removeFirst();
            ctx.write(frame.bytes, frame.promise);
            if (frame.type == ClientboundPackets.BUNDLE_DELIMITER) insideBundle = !insideBundle;
            else if (frame.type != null) connection.forwarded(frame.type, null);
        }
        if (output.flush) ctx.flush();
        if (!output.tasks.isEmpty()) {
            // Run after emission but before preparing the next queued packet.
            // A callback may allocate a transaction and send its ping; that ping
            // must precede proofs allocated while preparing subsequent packets.
            connection.beginWork();
            queued.addFirst(new Pending(
                    new CultConnection.WriteTask(() -> {
                        for (Runnable task : output.tasks)
                            CultNetworkManager.runDeferredPacketTask("tasksAfterSend", task);
                    }),
                    ctx.newPromise()));
        }
    }

    private void fail(ChannelHandlerContext ctx, Throwable failure) {
        try {
            ctx.fireExceptionCaught(failure);
        } finally {
            ctx.close();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        var failure = new ClosedChannelException();
        while (!queued.isEmpty()) {
            Operation operation = queued.removeFirst();
            if (operation instanceof Pending pending) pending.discard(failure);
            connection.endWork();
        }
    }

    private sealed interface Operation permits Pending, Flush {}

    private enum Flush implements Operation {
        INSTANCE
    }

    private record Pending(Object message, ChannelPromise promise) implements Operation {
        void discard(Throwable failure) {
            if (message instanceof ByteBuf bytes) bytes.release();
            promise.tryFailure(failure);
        }
    }

    private record Frame(ByteBuf bytes, ChannelPromise promise, PacketType<?> type) {}

    private record GroupCompletion(List<ChannelPromise> parts, ChannelPromise promise) {}

    /** Local bundle cursor during expansion; it never changes the emitted wire state. */
    private static final class Batch {
        final Output output = new Output();
        boolean inside;

        Batch(boolean inside) {
            this.inside = inside;
        }

        void add(Frame frame) {
            output.frames.addLast(frame);
            if (frame.type == ClientboundPackets.BUNDLE_DELIMITER) inside = !inside;
        }
    }

    /** Owns only un-emitted frames. Appending transfers ownership without retaining a tree. */
    private static final class Output {
        final ArrayDeque<Frame> frames = new ArrayDeque<>();
        final List<Runnable> tasks = new ArrayList<>();
        final List<GroupCompletion> groups = new ArrayList<>();
        boolean flush;

        void append(Output source) {
            frames.addAll(source.frames);
            tasks.addAll(source.tasks);
            groups.addAll(source.groups);
            flush |= source.flush;
            source.frames.clear();
            source.tasks.clear();
            source.groups.clear();
            source.flush = false;
        }

        void discard(Throwable failure) {
            while (!frames.isEmpty()) {
                Frame frame = frames.removeFirst();
                frame.bytes.release();
                frame.promise.tryFailure(failure);
            }
            for (GroupCompletion group : groups) group.promise.tryFailure(failure);
            groups.clear();
            tasks.clear();
        }
    }
}
