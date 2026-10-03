package ac.cult.cultac.utils.data;

import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.utils.math.Vector3dm;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Getter
@ToString
public class HitData {
    BlockPos position;
    Vector3dm blockHitLocation;
    BlockState state;
    Direction closestDirection;

    public HitData(BlockPos position, Vector3dm blockHitLocation, Direction closestDirection, BlockState state) {
        this.position = position;
        this.blockHitLocation = blockHitLocation;
        this.closestDirection = closestDirection;
        this.state = state;
    }

    public Vec3 getRelativeBlockHitLocation() {
        return new Vec3(
                blockHitLocation.getX() - position.getX(),
                blockHitLocation.getY() - position.getY(),
                blockHitLocation.getZ() - position.getZ());
    }
}
