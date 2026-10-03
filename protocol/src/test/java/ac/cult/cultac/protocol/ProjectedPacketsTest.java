package ac.cult.cultac.protocol;

import static ac.cult.cultac.protocol.ConnectionPhase.*;
import static ac.cult.cultac.protocol.PacketDirection.*;
import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.packet.ClientboundPackets;
import ac.cult.cultac.protocol.packet.Packets;
import ac.cult.cultac.protocol.packet.ServerboundPackets;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPacket;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPing;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundAcceptTeleportation;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectedPacketsTest {
    private static final ProtocolRuntime WIRE = ProtocolRuntime.create(ProtocolData.load(ProtocolVersion.V1_21_3));
    private static final PacketType<ModelValue> REGISTRY = new PacketType<>(
            "clientbound.registry_data",
            ModelValue.class,
            CLIENTBOUND,
            Set.of(CONFIGURATION),
            List.of("minecraft:registry_data"),
            ProtocolVersion.V26_3,
            new WritablePacketCodec<>() {
                @Override
                public boolean requiresModelValues() {
                    return true;
                }

                @Override
                public ModelValue read(ByteBuf bytes, ProtocolContext context) {
                    assertEquals(
                            ProtocolVersion.V26_3, context.version(), "Old bytes must never reach model value codecs");
                    return new ModelValue(Wire.readVarInt(bytes));
                }

                @Override
                public void write(ByteBuf bytes, ProtocolContext context, ModelValue value) {
                    assertEquals(ProtocolVersion.V26_3, context.version());
                    Wire.writeVarInt(bytes, value.id());
                }
            });
    private static final ProtocolRuntime MODEL = model();

    private static ProtocolRuntime model() {
        var catalog = new ArrayList<>(Packets.all());
        catalog.add(REGISTRY);
        return ProtocolRuntime.create(ProtocolData.load(ProtocolVersion.V26_3), catalog);
    }

    record ModelValue(int id) implements ClientboundPacket {}

    @Test
    void converterConsumptionPreservesTheOriginalOldTeleportAcknowledgment() {
        var projection = new Projection();
        try (var packets = new ProjectedPackets(WIRE, MODEL, () -> projection)) {
            ByteBuf physical = frame(WIRE, PLAY, SERVERBOUND, "accept_teleportation");
            Wire.writeVarInt(physical, 42);
            int index = physical.readerIndex();
            try {
                var values = packets.read(PLAY, SERVERBOUND, physical, CodecState.EMPTY, ignored -> true, true);
                assertEquals(1, values.size());
                assertSame(
                        ServerboundPackets.ACCEPT_TELEPORTATION, values.get(0).type());
                assertEquals(
                        new ServerboundAcceptTeleportation(42, null, 0, 0),
                        values.get(0).packet());
                assertFalse(values.get(0).derived());
                assertEquals(index, physical.readerIndex());
                assertEquals(1, physical.refCnt());
                assertArrayEquals(ByteBufUtil.getBytes(physical), projection.observed);
            } finally {
                physical.release();
            }
        }
        assertTrue(projection.closed);
    }

    @Test
    void synthesizedMovementIsNeverDispatchedAsASecondClientAction() {
        var projection = new Projection();
        ByteBuf synthetic = frame(MODEL, PLAY, SERVERBOUND, "move_player_status_only");
        synthetic.writeByte(1);
        projection.outputs =
                List.of(new PacketProjection.Frame(SERVERBOUND, PLAY, ByteBufUtil.getBytes(synthetic), true));
        synthetic.release();
        try (var packets = new ProjectedPackets(WIRE, MODEL, () -> projection)) {
            ByteBuf physical = frame(WIRE, PLAY, SERVERBOUND, "accept_teleportation");
            Wire.writeVarInt(physical, 42);
            try {
                var values = packets.read(PLAY, SERVERBOUND, physical, CodecState.EMPTY, ignored -> true, true);
                assertEquals(
                        List.of(ServerboundPackets.ACCEPT_TELEPORTATION),
                        values.stream().map(ProjectedPackets.Value::type).toList());
            } finally {
                physical.release();
            }
        }
    }

    @Test
    void derivedNativeRegistriesPrecedeTheOriginalFinishConfiguration() {
        var projection = new Projection();
        ByteBuf derived = Unpooled.buffer();
        MODEL.encode(CONFIGURATION, REGISTRY, new ModelValue(7), derived);
        ByteBuf finish = frame(MODEL, CONFIGURATION, CLIENTBOUND, "finish_configuration");
        projection.outputs = List.of(
                new PacketProjection.Frame(CLIENTBOUND, CONFIGURATION, ByteBufUtil.getBytes(derived), true),
                new PacketProjection.Frame(CLIENTBOUND, CONFIGURATION, ByteBufUtil.getBytes(finish), false));
        derived.release();
        finish.release();
        try (var packets = new ProjectedPackets(WIRE, MODEL, () -> projection)) {
            ByteBuf physical = frame(WIRE, CONFIGURATION, CLIENTBOUND, "finish_configuration");
            try {
                var values =
                        packets.read(CONFIGURATION, CLIENTBOUND, physical, CodecState.EMPTY, ignored -> true, true);
                assertEquals(
                        List.of(REGISTRY, ClientboundPackets.FINISH_CONFIGURATION),
                        values.stream().map(ProjectedPackets.Value::type).toList());
                assertEquals(new ModelValue(7), values.get(0).packet());
                assertTrue(values.get(0).derived());
                assertFalse(values.get(values.size() - 1).derived());
            } finally {
                physical.release();
            }
        }
    }

    @Test
    void pureAuthoredPacketsUseTheClientWireIdWithoutReverseConversion() {
        var projection = new Projection();
        try (var packets = new ProjectedPackets(WIRE, MODEL, () -> projection)) {
            var encoded = packets.encode(
                    PLAY,
                    ClientboundPackets.PING,
                    new ClientboundPing(123),
                    UnpooledByteBufAllocator.DEFAULT,
                    CodecState.EMPTY);
            try {
                assertFalse(encoded.destinationObserved());
                ByteBuf frame = encoded.frames().get(0);
                assertEquals(WIRE.data().packets(PLAY, CLIENTBOUND).id("minecraft:ping"), Wire.readVarInt(frame));
                assertEquals(123, frame.readInt());
                assertFalse(frame.isReadable());
                assertEquals(0, projection.authored);
            } finally {
                encoded.frames().forEach(ByteBuf::release);
            }
        }
    }

    @Test
    void nativeAuthoredPacketsRetainEveryConvertedPhysicalFrame() {
        var projection = new Projection();
        byte[] first = {1, 2}, second = {3, 4};
        projection.wireOutputs = List.of(
                new PacketProjection.Frame(CLIENTBOUND, CONFIGURATION, first, false),
                new PacketProjection.Frame(CLIENTBOUND, CONFIGURATION, second, true));
        try (var packets = new ProjectedPackets(WIRE, MODEL, () -> projection)) {
            var encoded = packets.encode(
                    CONFIGURATION, REGISTRY, new ModelValue(9), UnpooledByteBufAllocator.DEFAULT, CodecState.EMPTY);
            try {
                assertTrue(encoded.destinationObserved());
                assertEquals(2, encoded.frames().size());
                assertArrayEquals(first, ByteBufUtil.getBytes(encoded.frames().get(0)));
                assertArrayEquals(
                        second,
                        ByteBufUtil.getBytes(
                                encoded.frames().get(encoded.frames().size() - 1)));
                assertEquals(1, projection.authored);
            } finally {
                encoded.frames().forEach(ByteBuf::release);
            }
        }
    }

    private static ByteBuf frame(
            ProtocolRuntime runtime, ConnectionPhase phase, PacketDirection direction, String name) {
        ByteBuf bytes = Unpooled.buffer();
        int id = runtime.data().packets(phase, direction).id("minecraft:" + name);
        assertTrue(id >= 0, name);
        Wire.writeVarInt(bytes, id);
        return bytes;
    }

    private static final class Projection implements PacketProjection {
        List<Frame> outputs = List.of(), wireOutputs = List.of();
        byte[] observed;
        int authored;
        boolean closed;

        @Override
        public List<Frame> toModel(PacketDirection direction, ConnectionPhase phase, byte[] bytes, boolean mirror) {
            observed = bytes;
            return outputs;
        }

        @Override
        public List<Frame> toWire(PacketDirection direction, ConnectionPhase phase, byte[] bytes) {
            authored++;
            return wireOutputs;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
