package ac.cult.cultac.utils.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class MainSupportingBlockData {
    @Nullable
    BlockPos blockPos;

    boolean onGround;

    public boolean lastOnGroundAndNoBlock() {
        return blockPos == null && onGround;
    }
}
