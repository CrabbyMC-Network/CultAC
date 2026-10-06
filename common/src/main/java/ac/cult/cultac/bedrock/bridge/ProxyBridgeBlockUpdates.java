package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.bridge.wire.BlockUpdatesMessage;
import ac.cult.cultac.bedrock.player.BedrockBlockLayers;
import ac.cult.cultac.player.CultPlayer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

/** Returns an application callback for the actual native client receipt boundary. */
final class ProxyBridgeBlockUpdates {
    private ProxyBridgeBlockUpdates() { }
    static Runnable capture(CultPlayer player, byte[] body) {
        var message = BlockUpdatesMessage.decode(body);
        var states = new LinkedHashMap<BlockPos, BedrockBlockLayers>();
        for (var update : message.updates()) {
            var pos = new BlockPos(update.x(), update.y(), update.z());
            var state = ((CraftBlockData) Bukkit.createBlockData(update.state())).getState();
            states.compute(pos, (key, before) -> (before == null ? BedrockBlockLayers.fromJava(Blocks.AIR.defaultBlockState()) : before).withLayer(update.layer(), state));
        }
        return () -> states.forEach((pos, layers) -> player.compensatedWorld.handleServerBlockUpdate(pos, layers.combined(), player.lastTransactionReceived.get()));
    }
}
