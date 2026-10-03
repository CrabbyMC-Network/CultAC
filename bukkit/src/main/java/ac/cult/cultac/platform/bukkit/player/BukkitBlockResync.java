package ac.cult.cultac.platform.bukkit.player;

import ac.cult.cultac.network.protocol.util.FoliaCompatUtil;
import io.papermc.paper.math.Position;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Region-safe authoritative snapshots for Paper block resynchronization. */
final class BukkitBlockResync {
    private BukkitBlockResync() {}

    static void resend(
            Player recipient,
            int minBlockX,
            int minBlockY,
            int minBlockZ,
            int maxBlockX,
            int maxBlockY,
            int maxBlockZ) {
        Plugin plugin = ac.cult.cultac.platform.bukkit.CultACBukkitLoaderPlugin.LOADER;
        FoliaCompatUtil.runTaskForEntity(
                recipient,
                plugin,
                () -> {
                    World world = recipient.getWorld();
                    for (int chunkX = minBlockX >> 4; chunkX <= maxBlockX >> 4; chunkX++) {
                        for (int chunkZ = minBlockZ >> 4; chunkZ <= maxBlockZ >> 4; chunkZ++) {
                            int x1 = Math.max(minBlockX, chunkX << 4);
                            int x2 = Math.min(maxBlockX, (chunkX << 4) + 15);
                            int z1 = Math.max(minBlockZ, chunkZ << 4);
                            int z2 = Math.min(maxBlockZ, (chunkZ << 4) + 15);
                            Runnable snapshot =
                                    () -> snapshotChunk(recipient, plugin, world, x1, minBlockY, z1, x2, maxBlockY, z2);
                            // The player may have teleported since the request, or the box
                            // may straddle a region boundary. Entity ownership is not block ownership.
                            if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) snapshot.run();
                            else Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, snapshot);
                        }
                    }
                },
                null,
                0);
    }

    private static void snapshotChunk(
            Player recipient, Plugin plugin, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!world.isChunkLoaded(minX >> 4, minZ >> 4)) return;
        Map<Position, BlockData> changes = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    changes.put(
                            Position.block(x, y, z), world.getBlockAt(x, y, z).getBlockData());
                }
            }
        }
        if (changes.isEmpty()) return;
        Runnable send = () -> {
            // Discard an old world's snapshot if a teleport won the scheduling race.
            if (recipient.isOnline() && recipient.getWorld() == world) recipient.sendMultiBlockChange(changes, false);
        };
        if (Bukkit.isOwnedByCurrentRegion(recipient)) send.run();
        else FoliaCompatUtil.runTaskForEntity(recipient, plugin, send, null, 0);
    }
}
