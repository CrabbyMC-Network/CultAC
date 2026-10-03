package ac.cult.cultac.utils.blockplace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
public class GhostBlock {

    private final BlockPos position;
    private final ItemStack itemUsed;
    private final boolean placeTypeBlock;

    public boolean isPlaceTypeBlock() {
        return placeTypeBlock;
    }
}
