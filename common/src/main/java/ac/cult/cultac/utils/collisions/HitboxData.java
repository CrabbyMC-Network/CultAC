package ac.cult.cultac.utils.collisions;

import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.collisions.datatypes.CollisionBox;
import ac.cult.cultac.utils.collisions.datatypes.NoCollisionBox;
import net.minecraft.world.level.block.state.BlockState;

public final class HitboxData {
    private HitboxData() {}

    public static CollisionBox getBlockHitbox(CultPlayer player, BlockState block, int x, int y, int z) {
        if (player == null || player.compensatedWorld == null || block == null) {
            return NoCollisionBox.INSTANCE;
        }

        BlockState state = block;

        // Selection shapes may query neighboring blocks; pass Cult's compensated world, never the live Bukkit world.
        return ClientBlockShapes.visual(player, state, block, x, y, z);
    }
}
