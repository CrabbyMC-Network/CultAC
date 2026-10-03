package ac.cult.cultac.protocol.value;

import java.util.List;

/** Wire deltas in units of 1/4096 block; stepped paths retain each tick offset. */
public sealed interface EntityDelta permits EntityDelta.Linear, EntityDelta.Stepped {
    Linear ZERO = new Linear((short) 0, (short) 0, (short) 0);

    record Linear(short x, short y, short z) implements EntityDelta {}

    record Stepped(List<Step> steps) implements EntityDelta {
        public Stepped {
            steps = List.copyOf(steps);
        }
    }

    record Step(short x, short y, short z, int ticks) {}
}
