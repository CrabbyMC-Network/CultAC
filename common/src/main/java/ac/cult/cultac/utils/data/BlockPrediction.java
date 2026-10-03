package ac.cult.cultac.utils.data;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

@AllArgsConstructor
@Getter
@Setter
public class BlockPrediction {
    List<BlockPos> forBlockUpdate;
    BlockPos blockPosition;
    int originalBlockId;
    int predictedBlockId;
    Vec3 playerPosition;
    int sequence;

    public BlockPrediction(
            List<BlockPos> forBlockUpdate,
            BlockPos blockPosition,
            int originalBlockId,
            int predictedBlockId,
            Vec3 playerPosition) {
        this(forBlockUpdate, blockPosition, originalBlockId, predictedBlockId, playerPosition, 0);
    }
}
