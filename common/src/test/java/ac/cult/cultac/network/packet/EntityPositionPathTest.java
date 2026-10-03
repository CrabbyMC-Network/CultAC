package ac.cult.cultac.network.packet;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.value.EntityDelta;
import java.util.List;
import net.minecraft.network.protocol.game.VecDelta;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EntityPositionPathTest {
    @Test
    void linearAndSteppedPathsMatchNativeDecodingWithoutCommittingTheCodecBase() {
        for (Vec3 base : List.of(new Vec3(-0.0, 64.1, -3.7), new Vec3(30_000_000.0001, -0.0, -30_000_000.0001))) {
            for (short value : new short[] {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE}) {
                var first = new VecDelta.Stepped.DeltaStep(value, (short) 0, (short) -value, Integer.MIN_VALUE);
                var second = new VecDelta.Stepped.DeltaStep((short) 0, value, (short) 0, Integer.MAX_VALUE);
                compare(
                        base,
                        new VecDelta.Linear(first.xa(), first.ya(), first.za()),
                        new EntityDelta.Linear(first.xa(), first.ya(), first.za()));
                compare(
                        base,
                        new VecDelta.Stepped(List.of(first, second)),
                        new EntityDelta.Stepped(List.of(
                                new EntityDelta.Step(first.xa(), first.ya(), first.za(), first.ticks()),
                                new EntityDelta.Step(second.xa(), second.ya(), second.za(), second.ticks()))));
            }
        }
    }

    private static void compare(Vec3 base, VecDelta nativeDelta, EntityDelta delta) {
        var codec = new VecDeltaCodec();
        codec.setBase(base);
        var nativePath = nativeDelta.decode(codec);
        var expected = nativePath instanceof net.minecraft.world.entity.PositionPath.Stepped path
                ? new EntityPositionPath(
                        path.endPosition(),
                        path.steps().stream()
                                .map(step -> new EntityPositionPath.Step(step.position(), step.tickOffset()))
                                .toList())
                : EntityPositionPath.linear(nativePath.endPosition());
        var actual = EntityPositionPath.decodeRelative(delta, base);
        assertEquals(expected, actual);
        assertSame(base, codec.getBase());
        assertEquals(
                Double.doubleToRawLongBits(expected.endPosition().x),
                Double.doubleToRawLongBits(actual.endPosition().x));
        assertEquals(
                Double.doubleToRawLongBits(expected.endPosition().y),
                Double.doubleToRawLongBits(actual.endPosition().y));
        assertEquals(
                Double.doubleToRawLongBits(expected.endPosition().z),
                Double.doubleToRawLongBits(actual.endPosition().z));
    }
}
