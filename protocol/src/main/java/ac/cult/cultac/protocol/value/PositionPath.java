package ac.cult.cultac.protocol.value;

import java.util.List;
import java.util.Objects;

/** Absolute wire positions. A stepped path ends at its final step. */
public sealed interface PositionPath permits PositionPath.Linear, PositionPath.Stepped {
    Vec3d endPosition();

    record Linear(Vec3d endPosition) implements PositionPath {
        public Linear {
            Objects.requireNonNull(endPosition);
        }
    }

    record Stepped(List<Step> steps) implements PositionPath {
        public Stepped {
            steps = List.copyOf(steps);
            if (steps.isEmpty()) throw new IllegalArgumentException("A stepped position path needs an endpoint");
        }

        @Override
        public Vec3d endPosition() {
            return steps.get(steps.size() - 1).position();
        }
    }

    record Step(Vec3d position, int tickOffset) {
        public Step {
            Objects.requireNonNull(position);
        }
    }
}
