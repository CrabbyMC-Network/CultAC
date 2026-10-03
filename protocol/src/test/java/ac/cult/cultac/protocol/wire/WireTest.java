package ac.cult.cultac.protocol.wire;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolResolutionException;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.value.BlockPos;
import ac.cult.cultac.protocol.value.Relative;
import ac.cult.cultac.protocol.value.Vec3d;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WireTest {
    @Test
    void packetIdPeekPreservesIndicesAndMatchesVanillasNonMinimalEncodings() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (String hex : new String[]{"00", "7f", "8001", "ffffffff07", "8080808008", "ffffffff0f", "8000", "ffffffff7f"}) {
                buffer.clear().writeInt(123).writeBytes(ByteBufUtil.decodeHexDump(hex)).writeInt(456);
                buffer.readerIndex(4);
                int end = buffer.writerIndex();
                int id = Wire.peekVarInt(buffer);
                assertEquals(4, buffer.readerIndex());
                assertEquals(end, buffer.writerIndex());
                assertEquals(1, buffer.refCnt());
                assertEquals(Wire.readVarInt(buffer), id);
                assertEquals(456, buffer.readInt());
            }
            for (String malformed : new String[]{"", "80", "808080808000"}) {
                buffer.clear().writeInt(123).writeBytes(ByteBufUtil.decodeHexDump(malformed));
                buffer.readerIndex(4);
                int end = buffer.writerIndex();
                assertThrows(MalformedPacketException.class, () -> Wire.peekVarInt(buffer));
                assertEquals(4, buffer.readerIndex());
                assertEquals(end, buffer.writerIndex());
            }
        } finally { buffer.release(); }
    }

    @Test
    void integerBoundariesAndVanillaNonMinimalEncodings() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            int[] values = {0, 1, 127, 128, 255, 2147483647, -2147483648, -1};
            String[] hex = {"00", "01", "7f", "8001", "ff01", "ffffffff07", "8080808008", "ffffffff0f"};
            for (int i = 0; i < values.length; i++) {
                Wire.writeVarInt(buffer.clear(), values[i]);
                assertEquals(hex[i], ByteBufUtil.hexDump(buffer));
                assertEquals(values[i], Wire.readVarInt(buffer));
            }
            for (long value : new long[]{0, 1, 127, 128, Long.MAX_VALUE, Long.MIN_VALUE, -1}) {
                Wire.writeVarLong(buffer.clear(), value);
                assertEquals(value, Wire.readVarLong(buffer));
            }
            assertEquals(0, Wire.readVarInt(buffer.clear().writeByte(0x80).writeByte(0)));
            assertEquals(-1, Wire.readVarInt(buffer.clear().writeBytes(ByteBufUtil.decodeHexDump("ffffffff7f"))));
            assertThrows(MalformedPacketException.class, () -> Wire.readVarInt(buffer.clear().writeZero(0)));
            assertThrows(MalformedPacketException.class, () -> Wire.readVarInt(buffer.clear().writeByte(0x80)));
            assertThrows(MalformedPacketException.class, () -> Wire.readVarInt(buffer.clear().writeBytes(ByteBufUtil.decodeHexDump("808080808000"))));
            assertThrows(MalformedPacketException.class, () -> Wire.readVarLong(buffer.clear().writeBytes(ByteBufUtil.decodeHexDump("8080808080808080808000"))));
        } finally {
            buffer.release();
        }
    }

    @Test
    void packedPositionAndShortMotionRespectSignsAndQuantization() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (BlockPos value : new BlockPos[]{new BlockPos(0, 0, 0), new BlockPos(-1, -1, -1),
                    new BlockPos(-33554432, -2048, 33554431), new BlockPos(33554431, 2047, -33554432)}) {
                Wire.writeBlockPos(buffer.clear(), value);
                assertEquals(value, Wire.readBlockPos(buffer));
            }
            Wire.writeBlockPos(buffer.clear(), new BlockPos(-1, -1, -1));
            assertEquals("ffffffffffffffff", ByteBufUtil.hexDump(buffer));
            Wire.writeShortVelocity(buffer.clear(), new Vec3d(-9, 0.00024999, 9));
            assertEquals(new Vec3d(-3.9, 0.000125, 3.9), Wire.readShortVelocity(buffer));
            Wire.writeShortVelocity(buffer.clear(), new Vec3d(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
            assertEquals(new Vec3d(0, -3.9, 3.9), Wire.readShortVelocity(buffer));
            assertThrows(MalformedPacketException.class, () -> Wire.readShortVelocity(buffer.clear().writeZero(5)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void stringsUseUtf16LimitsAndRejectTruncationAndOversizedLengths() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (String value : new String[]{"", "a", "\u0000", "\u20ac", "\ud83d\ude80"}) {
                Wire.writeString(buffer.clear(), value, value.length());
                assertEquals(value, Wire.readString(buffer, value.length()));
            }
            assertThrows(IllegalArgumentException.class, () -> Wire.writeString(buffer.clear(), "\ud83d\ude80", 1));
            assertThrows(MalformedPacketException.class, () -> Wire.readString(buffer.clear().writeByte(1), 1));
            assertThrows(MalformedPacketException.class, () -> Wire.readString(buffer.clear().writeByte(4), 1));
            Wire.writeVarInt(buffer.clear(), -1);
            assertThrows(MalformedPacketException.class, () -> Wire.readString(buffer, 3));
            Wire.writeString(buffer.clear(), "abc", 3);
            assertThrows(MalformedPacketException.class, () -> Wire.readString(buffer, 2));
            buffer.clear().writeByte(1).writeByte(0x80);
            assertEquals("\ufffd", Wire.readString(buffer, 1));
        } finally {
            buffer.release();
        }
    }

    @Test
    void uuidRelativeFlagsAndAngles() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            UUID uuid = new UUID(0x123456789abcdef0L, 0xfedcba9876543210L);
            Wire.writeUuid(buffer, uuid);
            assertEquals("123456789abcdef0fedcba9876543210", ByteBufUtil.hexDump(buffer));
            assertEquals(uuid, Wire.readUuid(buffer));
            Set<Relative> relatives = Set.of(Relative.Y, Relative.Y_ROT, Relative.ROTATE_DELTA);
            Wire.writeRelatives(buffer.clear(), relatives);
            assertEquals("0000010a", ByteBufUtil.hexDump(buffer));
            Set<Relative> decoded = Wire.readRelatives(buffer);
            assertEquals(relatives, decoded);
            assertThrows(UnsupportedOperationException.class, () -> decoded.add(Relative.X));
            assertEquals(Set.of(Relative.values()), Wire.readRelatives(buffer.clear().writeInt(-1)));
            Wire.writeAngle(buffer.clear(), ProtocolVersion.V26_2, -0.5f);
            assertEquals(-360.0f / 256, Wire.readAngle(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void lowPrecisionZeroAndTruncation() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            Wire.writeLpVec3(buffer, new Vec3d(-0.0, Double.NaN, Double.MIN_VALUE));
            assertEquals("00", ByteBufUtil.hexDump(buffer));
            assertEquals(Vec3d.ZERO, Wire.readLpVec3(buffer));
            Wire.writeLpVec3(buffer.clear(), new Vec3d(1234.5, -8, 2));
            byte[] bytes = ByteBufUtil.getBytes(buffer);
            for (int length = 0; length < bytes.length; length++) {
                buffer.clear().writeBytes(bytes, 0, length);
                assertThrows(MalformedPacketException.class, () -> Wire.readLpVec3(buffer));
            }
        } finally {
            buffer.release();
        }
    }

    @Test
    void unsupportedProtocolsNeverSelectANearbyVersion() {
        for (ProtocolVersion version : ProtocolVersion.values()) {
            assertSame(version, ProtocolVersion.of(version.protocol()));
        }
        assertThrows(ProtocolResolutionException.class, () -> ProtocolVersion.of(1073742161));
        assertTrue(ProtocolVersion.V26_2.atLeast(ProtocolVersion.V1_21_11));
        for (int unsupported : new int[]{-1, ProtocolVersion.V1_21_3.protocol() - 1, ProtocolVersion.V26_3.protocol() + 1, 1073742160}) {
            assertThrows(ProtocolResolutionException.class, () -> ProtocolVersion.of(unsupported));
        }
    }
}
