package ac.cult.cultac.network.packet;

import static org.junit.Assert.assertEquals;

import ac.cult.cultac.network.protocol.ClientVersion;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

public final class PacketCodecUtilTest {
    private static final Vec3 INPUT = new Vec3(0.123456789D, -0.0784000015D, 4.5D);

    @Test
    public void alreadyDecodedLpMotionPreservesTheOriginalNativeConsumerNormalization() {
        var bytes = io.netty.buffer.Unpooled.buffer();
        var random = new java.util.Random(0x774);
        var values = new java.util.ArrayList<Vec3>();
        values.addAll(java.util.List.of(
                Vec3.ZERO,
                INPUT,
                new Vec3(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                new Vec3(-0.0, 3.051944088384301E-5, -1.7179869183E10)));
        for (int i = 0; i < 512; i++)
            values.add(new Vec3(random.nextGaussian() * 8, random.nextGaussian(), random.nextGaussian() * 32));
        try {
            for (Vec3 value : values) {
                var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(7, value);
                net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket.STREAM_CODEC.encode(
                        bytes.clear(), packet);
                Vec3 decoded = net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket.STREAM_CODEC
                        .decode(bytes)
                        .movement();
                for (var client :
                        new ClientVersion[] {ClientVersion.V_1_19, ClientVersion.V_1_21_9, ClientVersion.V_26_3}) {
                    // Before migration the helper read the native pre-encoding
                    // Vec3. The record sees the independently decoded bytes.
                    assertVecEquals(quantized(client, value), quantized(client, decoded));
                }
            }
        } finally {
            bytes.release();
        }
    }

    private static Vec3 quantized(ClientVersion client, Vec3 movement) {
        return PacketCodecUtil.quantizeClientboundVelocity(ClientVersion.V_26_3, client, movement);
    }

    @Test
    public void legacyCodecTruncatesTowardZeroAndClampsToSignedShort() {
        Vec3 encoded = PacketCodecUtil.quantizeLegacyVelocity(INPUT);
        assertEquals(987.0D / 8000.0D, encoded.x, 0.0D);
        assertEquals(-627.0D / 8000.0D, encoded.y, 0.0D);
        assertEquals(Short.MAX_VALUE / 8000.0D, encoded.z, 0.0D);

        Vec3 negativeClamp = PacketCodecUtil.quantizeLegacyVelocity(new Vec3(-4.5D, 0.0D, 0.0D));
        assertEquals(Short.MIN_VALUE / 8000.0D, negativeClamp.x, 0.0D);
    }

    @Test
    public void newServerToLegacyClientAppliesLpThenViaLegacyCodec() {
        Vec3 expected = PacketCodecUtil.quantizeLegacyVelocity(PacketCodecUtil.quantizeLpVec3(INPUT));
        Vec3 actual =
                PacketCodecUtil.quantizeClientboundVelocity(ClientVersion.V_1_21_9, ClientVersion.V_1_21_7, INPUT);
        assertVecEquals(expected, actual);
    }

    @Test
    public void legacyServerToNewClientAppliesLegacyThenViaLpCodec() {
        Vec3 expected = PacketCodecUtil.quantizeLpVec3(PacketCodecUtil.quantizeLegacyVelocity(INPUT));
        Vec3 actual =
                PacketCodecUtil.quantizeClientboundVelocity(ClientVersion.V_1_21_7, ClientVersion.V_1_21_9, INPUT);
        assertVecEquals(expected, actual);
    }

    @Test
    public void matchingEndpointCodecIsAppliedExactlyOnce() {
        assertVecEquals(
                PacketCodecUtil.quantizeLegacyVelocity(INPUT),
                PacketCodecUtil.quantizeClientboundVelocity(ClientVersion.V_1_21_7, ClientVersion.V_1_21_7, INPUT));
        assertVecEquals(
                PacketCodecUtil.quantizeLpVec3(INPUT),
                PacketCodecUtil.quantizeClientboundVelocity(ClientVersion.V_1_21_9, ClientVersion.V_1_21_9, INPUT));
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 0.0D);
        assertEquals(expected.y, actual.y, 0.0D);
        assertEquals(expected.z, actual.z, 0.0D);
    }
}
