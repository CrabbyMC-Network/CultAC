package ac.cult.cultac.checks.impl.prediction.runner;

import ac.cult.cultac.checks.impl.prediction.PredVector;
import ac.cult.cultac.checks.impl.prediction.SimulationContext;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public final class StartingVelocityOrderTest {
    @Test
    public void immutableEmptyAndSingleCandidatePredictionsDoNotThrow() {
        SimulationContext context = mock(SimulationContext.class);
        when(context.getTarget()).thenReturn(Vec3.ZERO);
        assertTrue(SimulationProcessor.sortedStartingVelocities(List.of(), context).isEmpty());
        PredVector candidate = new PredVector(Vec3.ZERO);
        assertSame(candidate, SimulationProcessor.sortedStartingVelocities(List.of(candidate), context).getFirst());
    }

    @Test
    public void immutableCandidatesRetainOrderingAndProvenanceWithoutChangingEngineOutput() {
        SimulationContext context = mock(SimulationContext.class);
        when(context.getTarget()).thenReturn(Vec3.ZERO);
        PredVector far = new PredVector(new Vec3(2, 0, 0));
        PredVector near = new PredVector(new Vec3(0.1, 0, 0));
        PredVector skipped = new PredVector(Vec3.ZERO);
        skipped.setTickSkip(true);
        List<PredVector> original = List.of(far, skipped, near);
        List<PredVector> sorted = SimulationProcessor.sortedStartingVelocities(original, context);
        assertEquals(List.of(skipped, near, far), sorted);
        assertEquals(List.of(far, skipped, near), original);
        assertSame(skipped, sorted.getFirst());
        assertSame(near, sorted.get(1));
    }
}
