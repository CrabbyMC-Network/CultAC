package ac.cult.cultac.platform.bukkit;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.platform.api.player.PlatformPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Owns the Bukkit world access used by explicit validation commands. */
final class BukkitValidationBlocks {
    private BukkitValidationBlocks() {}

    static void apply(PlatformPlayer player, int x, int y, int z, String state, boolean packetOnly, Runnable applied) {
        var blockData = Bukkit.createBlockData(state);
        Player nativePlayer = (Player) player.getNative();
        var world = nativePlayer.getWorld();
        var location = new Location(world, x, y, z);
        var api = CultAPI.INSTANCE;
        if (packetOnly) {
            api.getScheduler()
                    .getEntityScheduler()
                    .run(
                            player,
                            api.getGrimPlugin(),
                            () -> {
                                if (nativePlayer.getWorld() != world) return;
                                nativePlayer.sendBlockChange(location, blockData);
                                api.getScheduler()
                                        .getEntityScheduler()
                                        .runDelayed(player, api.getGrimPlugin(), applied, null, 2L);
                            },
                            null);
        } else {
            api.getScheduler()
                    .getRegionScheduler()
                    .execute(api.getGrimPlugin(), player.getWorld(), x >> 4, z >> 4, () -> {
                        location.getBlock().setBlockData(blockData, false);
                        applied.run();
                    });
        }
    }
}
