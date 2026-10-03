package ac.cult.cultac.utils.data;

import ac.cult.cultac.checks.impl.prediction.PredictionSetbackState;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;

@Getter
@Setter
public class SetbackPosWithVector {
    private final Vec3 pos;
    private Vec3 vector;
    private final int tick;
    private final PredictionSetbackState profileState;

    public SetbackPosWithVector(Vec3 pos, Vec3 vector, int tick) {
        this(pos, vector, tick, null);
    }

    public SetbackPosWithVector(Vec3 pos, Vec3 vector, int tick, PredictionSetbackState profileState) {
        this.pos = pos;
        this.vector = vector;
        this.tick = tick;
        this.profileState = profileState;
    }
}
