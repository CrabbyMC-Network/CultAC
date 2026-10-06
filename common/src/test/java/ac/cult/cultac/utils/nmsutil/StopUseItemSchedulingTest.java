package ac.cult.cultac.utils.nmsutil;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class StopUseItemSchedulingTest {
    @BeforeClass public static void bootstrap() {
        OfflineCultTestBootstrap.installConfig();
    }

    @Test public void clearActiveItemRunsOnlyInScheduledEntityCallback() {
        Player player = mock(Player.class);
        Plugin plugin = mock(Plugin.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.execute(eq(plugin), any(), isNull(), eq(0L))).thenReturn(true);
        IsUsingItem.stopUseItem(player, plugin);
        verify(player, never()).clearActiveItem();
        verify(player, never()).getActiveItem();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).execute(eq(plugin), task.capture(), isNull(), eq(0L));
        task.getValue().run();
        verify(player, times(1)).clearActiveItem();
    }

    @Test public void retiredEntityDoesNotFallBackToNetworkThread() {
        Player player = mock(Player.class);
        Plugin plugin = mock(Plugin.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.execute(eq(plugin), any(), isNull(), eq(0L))).thenReturn(false);
        IsUsingItem.stopUseItem(player, plugin);
        verify(player, never()).clearActiveItem();
    }

    @Test public void absentPlayerOrPluginIsNoOp() {
        Player player = mock(Player.class);
        IsUsingItem.stopUseItem(null, mock(Plugin.class));
        IsUsingItem.stopUseItem(player, null);
        verifyNoInteractions(player);
    }
}
