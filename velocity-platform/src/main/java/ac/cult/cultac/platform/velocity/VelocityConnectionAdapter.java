package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.network.CultConnection;
import ac.cult.cultac.network.PlatformConnection;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.minecraft.MinecraftRegistries;
import ac.cult.cultac.vanilla.VanillaRegistryState;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.util.concurrent.EventExecutor;
import net.kyori.adventure.text.Component;

final class VelocityConnectionAdapter implements PlatformConnection {
    private final ProxyServer proxy;
    private final Player nativePlayer;
    private final VelocityPlayer player;
    private final VanillaRegistryState state;
    private final ModelExecutor owner;
    private final Object bedrockBridge;
    private final ac.cult.cultac.protocol.PacketProjectionService codecs;

    VelocityConnectionAdapter(
            ProxyServer proxy,
            Player nativePlayer,
            VelocityPlayer player,
            VanillaRegistryState state,
            EventExecutor worker) {
        this(proxy, nativePlayer, player, state, worker, null, null);
    }

    VelocityConnectionAdapter(
            ProxyServer proxy,
            Player nativePlayer,
            VelocityPlayer player,
            VanillaRegistryState state,
            EventExecutor worker,
            ac.cult.cultac.protocol.PacketProjectionService codecs,
            ac.cult.cultac.network.PacketOwner bedrock) {
        this.proxy = proxy;
        this.nativePlayer = nativePlayer;
        this.player = player;
        this.state = state;
        // A Bedrock session stays on Geyser's tick loop, which orders its Bedrock and Java packets.
        this.owner = new ModelExecutor(bedrock == null ? worker : bedrock.executor(), state);
        this.bedrockBridge = bedrock == null ? null : bedrock.bedrockBridge();
        this.codecs = codecs;
    }

    EventExecutor owner() {
        return owner;
    }

    Object bedrockBridge() {
        return bedrockBridge;
    }

    @Override
    public void runInModel(Runnable task) {
        state.execute(task);
    }

    VanillaRegistryState state() {
        return state;
    }

    void attach(CultConnection connection) {
        player.attach(connection);
    }

    @Override
    public MinecraftRegistries registries() {
        return state.context();
    }

    @Override
    public ac.cult.cultac.protocol.ProtocolVersion wireVersion() {
        return nativePlayer.getProtocolVersion() == null
                ? null
                : ac.cult.cultac.protocol.ProtocolVersion.of(
                        nativePlayer.getProtocolVersion().getProtocol());
    }

    @Override
    public ac.cult.cultac.protocol.PacketProjection packetProjection() {
        if (!owner.inEventLoop()) throw new IllegalStateException("Projection creation outside its packet owner");
        return codecs.connection(
                wireVersion(),
                ac.cult.cultac.protocol.ProtocolVersion.V26_3,
                nativePlayer.getUniqueId(),
                nativePlayer.getUsername());
    }

    @Override
    public User.Profile authenticatedProfile() {
        return new User.Profile(nativePlayer.getUniqueId(), nativePlayer.getUsername());
    }

    @Override
    public void disconnect(Component reason) {
        nativePlayer.disconnect(reason);
    }

    @Override
    public void sendMessage(Component message) {
        nativePlayer.sendMessage(message);
    }

    @Override
    public PlayerBinding playerBinding() {
        return new PlayerBinding() {
            @Override
            public VelocityPlayer player() {
                return player;
            }

            @Override
            public boolean isCurrent() {
                return nativePlayer.isActive()
                        && proxy.getPlayer(nativePlayer.getUniqueId()).orElse(null) == nativePlayer;
            }

            @Override
            public boolean matches(Object candidate) {
                return candidate == nativePlayer;
            }

            @Override
            public void initialize(CultPlayer target) {
                // Join/respawn packets supply client-visible entity, dimension and game mode.
                // Velocity has no authoritative world state to copy during configuration.
            }
        };
    }
}
