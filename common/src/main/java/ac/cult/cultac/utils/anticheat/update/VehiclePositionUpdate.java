package ac.cult.cultac.utils.anticheat.update;

import ac.cult.cultac.utils.data.TeleportAcceptData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;

@AllArgsConstructor
@Getter
@Setter
public class VehiclePositionUpdate {
    private final Vec3 from, to;
    private final float xRot, yRot;
    private final boolean onGround;
    private final boolean hasOnGround;
    private final TeleportAcceptData teleportAcceptData;
}
