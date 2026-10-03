package ac.cult.cultac.codec;

import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.PacketProjection;
import ac.cult.cultac.protocol.ProtocolResolutionException;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.wire.Wire;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.exception.CancelException;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Paired private endpoints observe the same stream; neither can emit into the real channel. */
final class CodecConnection implements PacketProjection {
    private static final Set<String> MIRROR_CONTROLS =
            Set.of("minecraft:accept_teleportation", "minecraft:move_player_pos_rot", "minecraft:player_loaded");
    private final Endpoint forward, backward;
    private final ProtocolVersion model;
    private final Consumer<CodecConnection> removal;
    private boolean closed;

    CodecConnection(
            ProtocolVersion wire, ProtocolVersion model, UUID id, String username, Consumer<CodecConnection> removal) {
        this.model = model;
        this.removal = removal;
        forward = new Endpoint(model, wire, id, username);
        try {
            backward = new Endpoint(wire, model, id, username);
        } catch (RuntimeException | Error failure) {
            forward.close();
            throw failure;
        }
    }

    @Override
    public List<Frame> toModel(PacketDirection direction, ConnectionPhase phase, byte[] bytes, boolean mirror) {
        checkOpen();
        Endpoint upgrade = direction == PacketDirection.CLIENTBOUND ? forward : backward;
        Endpoint paired = direction == PacketDirection.CLIENTBOUND ? backward : forward;
        var output = new ArrayList<Frame>();
        for (Frame original : upgrade.transform(direction, phase, bytes)) {
            requireDirection(original, direction);
            byte[] values = original.bytes();
            if (model == ProtocolVersion.V26_3
                    && direction == PacketDirection.CLIENTBOUND
                    && phase == ConnectionPhase.CONFIGURATION
                    && name(model, original).equals("minecraft:registry_data")) {
                values = ModelRegistryProjection.enchantments(values);
            }
            var frame = new Frame(direction, phase, values, original.generated());
            output.add(frame);
            if (mirror) {
                for (var mirrored : paired.transform(direction, phase, values)) {
                    if (mirrored.generated()) quarantine(paired, mirrored);
                }
            }
        }
        return List.copyOf(output);
    }

    @Override
    public List<Frame> toWire(PacketDirection direction, ConnectionPhase phase, byte[] bytes) {
        checkOpen();
        // The destination side already observes the authored native packet here.
        // Callers subsequently observe its client-facing output with mirror=false.
        Endpoint destination = direction == PacketDirection.CLIENTBOUND ? backward : forward;
        var output = destination.transform(direction, phase, bytes);
        for (Frame frame : output) requireDirection(frame, direction);
        return output;
    }

    private static void requireDirection(Frame frame, PacketDirection expected) {
        if (frame.direction() != expected)
            throw new ProtocolResolutionException(
                    "Private conversion generated an unexpected opposite-direction packet");
    }

    private static void quarantine(Endpoint endpoint, Frame frame) {
        ProtocolVersion version = frame.direction() == PacketDirection.SERVERBOUND ? endpoint.server : endpoint.client;
        String name = name(version, frame);
        if (frame.direction() == PacketDirection.SERVERBOUND && MIRROR_CONTROLS.contains(name)) return;
        // Old clients have a bed block entity. ViaBackwards derives this visual payload
        // from a bed block update/chunk; it has no counterpart in the newer native model.
        if (frame.direction() == PacketDirection.CLIENTBOUND && name.equals("minecraft:block_entity_data")) return;
        throw new ProtocolResolutionException("Unexpected generated mirror packet " + frame.direction() + "/" + name);
    }

    private static String name(ProtocolVersion version, Frame frame) {
        ByteBuf bytes = Unpooled.wrappedBuffer(frame.bytes());
        try {
            return ProtocolData.load(version)
                    .packets(frame.phase(), frame.direction())
                    .name(Wire.readVarInt(bytes));
        } finally {
            bytes.release();
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Projection is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            forward.close();
        } finally {
            backward.close();
            removal.accept(this);
        }
    }

    private static final class Endpoint extends UserConnectionImpl {
        final ProtocolVersion client, server;
        final List<Frame> emissions = new ArrayList<>();
        private ConnectionPhase phase;

        Endpoint(ProtocolVersion client, ProtocolVersion server, UUID id, String username) {
            super(new EmbeddedChannel());
            this.client = client;
            this.server = server;
            try {
                var info = getProtocolInfo();
                info.setProtocolVersion(
                        com.viaversion.viaversion.api.protocol.version.ProtocolVersion.getProtocol(client.protocol()));
                info.setServerProtocolVersion(
                        com.viaversion.viaversion.api.protocol.version.ProtocolVersion.getProtocol(server.protocol()));
                info.setUuid(id);
                info.setUsername(username);
                info.setState(State.CONFIGURATION);
                var pipeline = new ProtocolPipelineImpl(this);
                var protocols = Via.getManager().getProtocolManager();
                for (var base : protocols.getBaseProtocols(info.protocolVersion(), info.serverProtocolVersion()))
                    pipeline.add(base);
                var path = protocols.getProtocolPath(info.protocolVersion(), info.serverProtocolVersion());
                if (path == null)
                    throw new ProtocolResolutionException("No private conversion path " + client + " -> " + server);
                for (var entry : path) {
                    protocols.completeMappingDataLoading(entry.protocol().getClass());
                    pipeline.add(entry.protocol());
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        List<Frame> transform(PacketDirection direction, ConnectionPhase phase, byte[] bytes) {
            if (phase != ConnectionPhase.CONFIGURATION && phase != ConnectionPhase.PLAY)
                throw new ProtocolResolutionException("Private conversion is only valid after authentication");
            this.phase = phase;
            State state = State.valueOf(phase.name());
            getProtocolInfo().setState(state);
            emissions.clear();
            ByteBuf input = Unpooled.wrappedBuffer(bytes);
            try {
                var wrapper = PacketWrapper.create(Wire.readVarInt(input), input, this);
                try {
                    getProtocolInfo().getPipeline().transform(Direction.valueOf(direction.name()), state, wrapper);
                    ByteBuf output = Unpooled.buffer();
                    try {
                        wrapper.writeToBuffer(output);
                        emissions.add(new Frame(direction, phase, ByteBufUtil.getBytes(output), false));
                    } finally {
                        output.release();
                    }
                } catch (CancelException cancelled) {
                    /* Model cancellation never cancels physical input. */
                }
                ((EmbeddedChannel) getChannel()).runPendingTasks();
                return List.copyOf(emissions);
            } catch (Exception failure) {
                throw new ProtocolResolutionException(
                        "Private conversion failed for " + direction + "/" + phase + ": " + failure.getMessage(),
                        failure);
            } finally {
                input.release();
                emissions.clear();
            }
        }

        private void capture(PacketDirection direction, ByteBuf bytes) {
            try {
                emissions.add(new Frame(direction, phase, ByteBufUtil.getBytes(bytes), true));
            } finally {
                bytes.release();
            }
        }

        @Override
        public void sendRawPacket(ByteBuf bytes) {
            capture(PacketDirection.CLIENTBOUND, bytes);
        }

        @Override
        public void scheduleSendRawPacket(ByteBuf bytes) {
            capture(PacketDirection.CLIENTBOUND, bytes);
        }

        @Override
        public ChannelFuture sendRawPacketFuture(ByteBuf bytes) {
            capture(PacketDirection.CLIENTBOUND, bytes);
            return getChannel().newSucceededFuture();
        }

        @Override
        public void sendRawPacketToServer(ByteBuf bytes) {
            capture(PacketDirection.SERVERBOUND, bytes);
        }

        @Override
        public void scheduleSendRawPacketToServer(ByteBuf bytes) {
            capture(PacketDirection.SERVERBOUND, bytes);
        }

        void close() {
            ((EmbeddedChannel) getChannel()).finishAndReleaseAll();
            emissions.clear();
        }
    }
}
