package ac.cult.cultac.platform.api.player;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformPlayerCacheTest {
    @Test
    void oldConnectionCannotEvictItsReplacement() {
        var cache = PlatformPlayerCache.getInstance();
        UUID uuid = UUID.randomUUID();
        PlatformPlayer old = player(uuid, new Object());
        Object replacementNative = new Object();
        PlatformPlayer replacement = player(uuid, replacementNative);
        try {
            assertSame(old, cache.addOrGetPlayer(uuid, old));
            assertSame(replacement, cache.addOrGetPlayer(uuid, replacement));
            assertSame(replacement, cache.addOrGetPlayer(uuid, player(uuid, replacementNative)));
            cache.removePlayer(old);
            assertSame(replacement, cache.getPlayer(uuid));
            cache.removePlayer(replacement);
            assertNull(cache.getPlayer(uuid));
        } finally {
            cache.removePlayer(uuid);
        }
    }

    private static PlatformPlayer player(UUID uuid, Object nativePlayer) {
        PlatformPlayer player = mock(PlatformPlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getNative()).thenReturn(nativePlayer);
        return player;
    }
}
