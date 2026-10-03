package ac.cult.cultac.utils.data.packetentity;

import ac.cult.cultac.player.CultPlayer;
import net.minecraft.world.entity.EntityType;
import org.bukkit.block.BlockFace;

public class PacketEntityShulker extends PacketEntity {
    public BlockFace facing = BlockFace.DOWN;

    public PacketEntityShulker(CultPlayer player, int entityId, EntityType type, double x, double y, double z) {
        super(player, entityId, type, x, y, z);
    }
}
