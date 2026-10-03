package ac.cult.cultac.platform.velocity;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ModelIdMappings;
import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityCodecsTest {
    @TempDir
    Path directory;

    @Test
    void privateCodecsConvertBothDirectionsWithoutPlatformInjection() throws Exception {
        String marker = System.getProperty("ViaVersion");
        try (var codecs = new VelocityCodecs(directory)) {
            for (var version : List.of(ProtocolVersion.V1_21_3, ProtocolVersion.V1_21_11, ProtocolVersion.V26_2)) {
                try (var connection =
                        codecs.service().connection(version, ProtocolVersion.V26_3, UUID.randomUUID(), "CodecTest")) {
                    ByteBuf physical = frame(version, PacketDirection.CLIENTBOUND, "block_update");
                    physical.writeLong(64L);
                    Wire.writeVarInt(physical, 10);
                    byte[] original = ByteBufUtil.getBytes(physical);
                    try {
                        var converted =
                                connection.toModel(PacketDirection.CLIENTBOUND, ConnectionPhase.PLAY, original, true);
                        assertEquals(1, converted.size());
                        ByteBuf output =
                                Unpooled.wrappedBuffer(converted.getFirst().bytes());
                        try {
                            assertEquals(
                                    ProtocolData.load(ProtocolVersion.V26_3)
                                            .packets(ConnectionPhase.PLAY, PacketDirection.CLIENTBOUND)
                                            .id("minecraft:block_update"),
                                    Wire.readVarInt(output));
                            assertEquals(64L, output.readLong());
                            assertEquals(
                                    ModelIdMappings.load(version, ProtocolVersion.V26_3)
                                            .blockState(10),
                                    Wire.readVarInt(output));
                            assertFalse(output.isReadable());
                        } finally {
                            output.release();
                        }
                        assertArrayEquals(original, ByteBufUtil.getBytes(physical));
                    } finally {
                        physical.release();
                    }
                    physical = frame(version, PacketDirection.SERVERBOUND, "set_carried_item");
                    physical.writeShort(2);
                    try {
                        var converted = connection.toModel(
                                PacketDirection.SERVERBOUND,
                                ConnectionPhase.PLAY,
                                ByteBufUtil.getBytes(physical),
                                true);
                        assertEquals(1, converted.size());
                        ByteBuf output =
                                Unpooled.wrappedBuffer(converted.getFirst().bytes());
                        try {
                            assertEquals(
                                    ProtocolData.load(ProtocolVersion.V26_3)
                                            .packets(ConnectionPhase.PLAY, PacketDirection.SERVERBOUND)
                                            .id("minecraft:set_carried_item"),
                                    Wire.readVarInt(output));
                            assertEquals(2, output.readShort());
                            assertFalse(output.isReadable());
                        } finally {
                            output.release();
                        }
                    } finally {
                        physical.release();
                    }
                }
            }
        }
        assertEquals(marker, System.getProperty("ViaVersion"));
    }

    @Test
    void privateLoaderIgnoresParentMappingResourcesAndSharesTheProtocolSpi() throws Exception {
        URL jar = getClass().getResource("/runtime/protocol-codecs.jar");
        assertNotNull(jar);
        // URLClassLoader accepts the extracted file, not the enclosing engine resource URL.
        Path extracted = directory.resolve("codecs.jar");
        try (var input = jar.openStream()) {
            java.nio.file.Files.copy(input, extracted);
        }
        URL poison = new URL("file:/unrelated-proxy-mappings/");
        var parent = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return name.startsWith("assets/viaversion/") ? poison : super.getResource(name);
            }
        };
        try (var loader = new VelocityCodecs.CodecLoader(extracted.toUri().toURL(), parent)) {
            String resource = "assets/viaversion/data/mappings-26.2to26.3.nbt";
            assertNotNull(loader.getResource(resource));
            assertNotEquals(poison, loader.getResource(resource));
            assertEquals(
                    1, java.util.Collections.list(loader.getResources(resource)).size());
            assertSame(
                    ac.cult.cultac.protocol.PacketProjectionService.class,
                    loader.loadClass(ac.cult.cultac.protocol.PacketProjectionService.class.getName()));
            assertSame(
                    loader,
                    loader.loadClass("com.viaversion.viaversion.api.Via").getClassLoader());
        }
    }

    private static ByteBuf frame(ProtocolVersion version, PacketDirection direction, String name) {
        ByteBuf bytes = Unpooled.buffer();
        int id = ProtocolData.load(version)
                .packets(ConnectionPhase.PLAY, direction)
                .id("minecraft:" + name);
        assertTrue(id >= 0, name);
        Wire.writeVarInt(bytes, id);
        return bytes;
    }
}
