package ac.cult.cultac.protocol.netty;

import ac.cult.cultac.network.CultConnection;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.protocol.ConnectionLifecycle;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayDeque;

/** I/O owns read demand and forwarding; the packet owner processes one frame at a time. */
public final class CultDecoder extends ChannelDuplexHandler {
    public static final String NAME = "cult-decoder";
    private static final Object COMPRESSION_EVENT = compressionEvent();

    private final CultConnection connection;
    private final boolean resumeAfterTerminal;
    private final ArrayDeque<ByteBuf> queued = new ArrayDeque<>();
    private boolean inFlight;
    private boolean resumeRequested;
    private boolean hostPaused;
    private volatile boolean removed;

    public CultDecoder(CultConnection connection) {
        this(connection, false);
    }

    /** Velocity switches its client handler synchronously; Paper resumes after an async configuration task. */
    public CultDecoder(CultConnection connection, boolean resumeAfterTerminal) {
        this.connection = connection;
        this.resumeAfterTerminal = resumeAfterTerminal;
    }

    public static void install(CultConnection connection) {
        var pipeline = connection.channel().pipeline();
        String before = pipeline.get("decoder") != null ? "decoder" : "inbound_config";
        pipeline.addBefore(before, NAME, new CultDecoder(connection));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof ByteBuf frame)) {
            ctx.fireChannelRead(message);
            return;
        }
        connection.beginWork();
        if (inFlight || hostPaused) queued.addLast(frame);
        else start(ctx, frame);
    }

    private void start(ChannelHandlerContext ctx, ByteBuf frame) {
        // Protocol setup may synchronously read another LocalChannel frame. Keep
        // that read behind both forwarding and our corresponding phase change.
        inFlight = true;
        try {
            connection.resolveOwner();
        } catch (Throwable failure) {
            frame.release();
            complete(ctx, null, failure, false);
            return;
        }
        boolean pull = connection.owner() != ctx.executor();
        if (!pull) {
            dispatch(ctx, frame, false);
            return;
        }
        ctx.channel().config().setAutoRead(false);
        try {
            connection.owner().execute(() -> dispatch(ctx, frame, true));
        } catch (RuntimeException rejected) {
            frame.release();
            complete(ctx, null, rejected, true);
        }
    }

    private void dispatch(ChannelHandlerContext ctx, ByteBuf frame, boolean pull) {
        Forward output = null;
        Throwable failure = null;
        try {
            if (removed || !ctx.channel().isActive()) frame.release();
            else output = process(frame);
        } catch (Throwable caught) {
            failure = caught;
        }
        if (!pull) {
            complete(ctx, output, failure, false);
            return;
        }
        Forward result = output;
        Throwable error = failure;
        try {
            ctx.executor().execute(() -> complete(ctx, result, error, true));
        } catch (RuntimeException rejected) {
            // I/O can shut down while the owner is processing. No callback will
            // take ownership of this result when its return submission fails.
            if (result != null) result.frame.release();
            try {
                ctx.channel().close();
            } finally {
                connection.endWork();
            }
        }
    }

    private void complete(ChannelHandlerContext ctx, Forward output, Throwable failure, boolean pull) {
        try {
            // Restore the proxy's read setting before handing it the packet. Its
            // handler may now pause for an asynchronous PluginMessageEvent. The
            // in-flight barrier retains any synchronous read caused by this toggle.
            if (pull && resumeAfterTerminal && !removed && ctx.channel().isActive())
                ctx.channel().config().setAutoRead(true);
            if (failure != null) fail(ctx, failure);
            else if (output != null) {
                if (removed || !ctx.channel().isActive()) output.frame.release();
                else {
                    ctx.fireChannelRead(output.frame);
                    if (output.type != null) connection.forwarded(output.type, output.packet);
                }
            }
        } catch (Throwable caught) {
            fail(ctx, caught);
        } finally {
            inFlight = false;
            boolean resume = resumeRequested;
            resumeRequested = false;
            boolean terminal = output != null && ConnectionLifecycle.handles(output.type);
            hostPaused = resumeAfterTerminal && !ctx.channel().config().isAutoRead();
            try {
                // In pull mode, a terminal waits for vanilla's configuration task
                // to resume reads. Ordinary I/O only replays deferred read demand.
                if (!removed
                        && !hostPaused
                        && ctx.channel().isActive()
                        && (resume || (pull && (!terminal || resumeAfterTerminal)))) read(ctx);
            } finally {
                connection.endWork();
            }
        }
    }

    /** Consumes the input on every path; null means it was cancelled or discarded. */
    @SuppressWarnings("unchecked")
    private Forward process(ByteBuf frame) {
        try {
            var phase = connection.phase(PacketDirection.SERVERBOUND);
            PacketType<?> forwardedType = null;
            ServerboundPacket forwardedPacket = null;
            for (var value : connection
                    .packets()
                    .read(
                            phase,
                            PacketDirection.SERVERBOUND,
                            frame,
                            connection,
                            type -> connection.dispatcher().get(type) != null,
                            true)) {
                var route = connection.dispatcher().get(value.type());
                var type = (PacketType<ServerboundPacket>) route.type();
                var original = (ServerboundPacket) value.packet();
                connection.prepare();
                var event = new PacketReceiveEvent<>(connection.user(), phase, type, original);
                if (route.receive() != null) connection.dispatcher().receive(event, route.receive());
                ServerboundPacket packet = event.getPacket();
                boolean cancelled = event.isCancelled();
                var player = connection.player();
                // A setback consumes only an original position move and wins over
                // listener cancellation/replacement, retaining the client's rotation.
                if (!value.derived()
                        && original instanceof ServerboundMovePlayer move
                        && move.hasPosition()
                        && player != null) {
                    var pending = player.getSetbackTeleportUtil().takePendingServerMove();
                    if (pending != null) {
                        packet = move.withPosition(pending.x(), pending.y(), pending.z(), pending.onGround());
                        cancelled = false;
                    }
                }
                if (value.derived()) {
                    if (cancelled || packet != original)
                        throw new IllegalStateException("Cannot mutate a derived model packet " + type);
                    continue;
                }
                if (cancelled) return null;
                if (packet != original) {
                    var replacement = connection
                            .packets()
                            .encode(phase, type, packet, connection.channel().alloc(), connection);
                    if (replacement.frames().size() != 1) {
                        replacement.frames().forEach(ByteBuf::release);
                        throw new IllegalStateException("Inbound replacement must encode one physical frame");
                    }
                    frame.release();
                    frame = replacement.frames().getFirst();
                }
                if (packet instanceof ServerboundMovePlayer move
                        && move.hasPosition()
                        && player != null
                        && connection.user() != null
                        && connection.user().getBedrockBridgeConnection() != null) {
                    player.getSetbackTeleportUtil()
                            .setBedrockPaperVisiblePosition(
                                    new net.minecraft.world.phys.Vec3(move.x(), move.y(), move.z()));
                }
                forwardedType = type;
                forwardedPacket = packet;
            }
            var output = new Forward(frame, forwardedType, forwardedPacket);
            frame = null;
            return output;
        } finally {
            if (frame != null) frame.release();
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
    public void read(ChannelHandlerContext ctx) {
        if (inFlight) {
            resumeRequested = true;
            return;
        }
        hostPaused = false;
        if (!queued.isEmpty()) start(ctx, queued.removeFirst());
        else ctx.read();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        while (!queued.isEmpty()) {
            queued.removeFirst().release();
            connection.endWork();
        }
        if (ctx.channel().isActive() && ctx.pipeline().get("decoder") != null) {
            ctx.channel().config().setAutoRead(true);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        if (event == COMPRESSION_EVENT && connection.relocateCompressionOnce()) {
            ctx.pipeline().remove(NAME);
            ctx.pipeline().remove(CultEncoder.NAME);
            install(connection);
            CultEncoder.install(connection);
        }
        ctx.fireUserEventTriggered(event);
    }

    private static Object compressionEvent() {
        try {
            return Class.forName("io.papermc.paper.network.ConnectionEvent")
                    .getField("COMPRESSION_THRESHOLD_SET")
                    .get(null);
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    private record Forward(ByteBuf frame, PacketType<?> type, Object packet) {}
}
