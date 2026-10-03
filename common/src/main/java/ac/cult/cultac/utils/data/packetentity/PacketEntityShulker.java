package ac.cult.cultac.utils.data.packetentity;

import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.value.Direction;
import net.minecraft.world.entity.EntityType;

public class PacketEntityShulker extends PacketEntity {
    public Direction facing = Direction.DOWN;

    public PacketEntityShulker(CultPlayer player, int entityId, EntityType type, double x, double y, double z) {
        super(player, entityId, type, x, y, z);
    }
}
