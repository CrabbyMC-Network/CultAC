package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.platform.api.player.AbstractPlatformPlayerFactory;
import ac.cult.cultac.platform.api.player.OfflinePlatformPlayer;
import ac.cult.cultac.platform.api.player.PlatformPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VelocityPlayers extends AbstractPlatformPlayerFactory<Player> {
    private final ProxyServer proxy;
    private final VelocitySenders senders;
    private final Map<UUID, KnownPlayer> known = new ConcurrentHashMap<>();

    VelocityPlayers(ProxyServer proxy, VelocitySenders senders) {
        this.proxy = proxy;
        this.senders = senders;
    }

    @Override
    protected Player getNativePlayer(UUID id) {
        return proxy.getPlayer(id).orElse(null);
    }

    @Override
    protected Player getNativePlayer(String name) {
        return proxy.getPlayer(name).orElse(null);
    }

    @Override
    protected UUID getPlayerUUID(Player player) {
        return player.getUniqueId();
    }

    @Override
    protected Collection<Player> getNativeOnlinePlayers() {
        return List.copyOf(proxy.getAllPlayers());
    }

    @Override
    protected PlatformPlayer createPlatformPlayer(Player player) {
        known.put(player.getUniqueId(), new KnownPlayer(player.getUniqueId(), player.getUsername()));
        return new VelocityPlayer(player, senders);
    }

    @Override
    public OfflinePlatformPlayer getOfflineFromUUID(UUID id) {
        var online = getFromUUID(id);
        return online != null ? online : known.computeIfAbsent(id, key -> new KnownPlayer(key, key.toString()));
    }

    @Override
    public OfflinePlatformPlayer getOfflineFromName(String name) {
        var online = getFromName(name);
        return online != null
                ? online
                : known.values().stream()
                        .filter(player -> player.name.equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);
    }

    @Override
    public Collection<OfflinePlatformPlayer> getOfflinePlayers() {
        return List.copyOf(known.values());
    }

    private final class KnownPlayer implements OfflinePlatformPlayer {
        private final UUID id;
        private final String name;

        KnownPlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public UUID getUniqueId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isOnline() {
            return proxy.getPlayer(id).isPresent();
        }
    }
}
