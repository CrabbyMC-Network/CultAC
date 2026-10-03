package ac.cult.cultac.utils.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.world.phys.Vec3;

@Data
@AllArgsConstructor
public class StuckSpeedData {
    Vec3 stuckSpeedMultiplier;
    Vec3 unknownStuckSpeedMultiplier;
}
