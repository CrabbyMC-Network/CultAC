package ac.cult.placement.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only vanilla geometry and registry data. All boxes are relative to the queried block. */
public interface BlockGeometry {
    enum Shape {
        COLLISION,
        OUTLINE,
        VISUAL,
        INTERACTION,
        SUPPORT,
        OCCLUSION
    }

    /**
     * Client-known inputs used by vanilla's collision context, without a live Entity.
     * A null context requests vanilla's empty context (whose isAbove uses the supplied default).
     * Entity-specific powder snow and moving-piston geometry belongs to the compensated
     * simulation and must be resolved there before querying this stateless block service.
     */
    record Context(double entityBottom, boolean descending, String heldItem, boolean placement) {
        public Context {
            Objects.requireNonNull(heldItem, "heldItem");
        }
    }

    record State(
            int id,
            String block,
            Map<String, String> properties,
            boolean air,
            boolean replaceable,
            float friction,
            float speedFactor,
            float jumpFactor) {
        public State {
            Objects.requireNonNull(block, "block");
            properties = Map.copyOf(properties);
        }
    }

    /** The explicit state can differ from the world at pos during speculative placement or translation. */
    List<PlacementEngine.Box> shape(
            PlacementEngine.World world, PlacementEngine.Pos pos, int state, Shape shape, Context context);

    State state(int id);
}
