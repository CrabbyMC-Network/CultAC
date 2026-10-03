package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.platform.api.PlatformServer;
import ac.cult.cultac.platform.api.player.PlatformPlayer;
import ac.cult.cultac.platform.api.sender.Sender;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

final class VelocityServer implements PlatformServer {
    private final ProxyServer proxy;
    private final VelocitySenders senders;

    VelocityServer(ProxyServer proxy, VelocitySenders senders) {
        this.proxy = proxy;
        this.senders = senders;
    }

    @Override
    public String getPlatformImplementationString() {
        return proxy.getVersion().getName() + " " + proxy.getVersion().getVersion();
    }

    @Override
    public void dispatchCommand(Sender sender, String command) {
        proxy.getCommandManager().executeAsync(senders.unwrap(sender), command);
    }

    @Override
    public Sender getConsoleSender() {
        return senders.wrap(proxy.getConsoleCommandSource());
    }

    @Override
    public void registerOutgoingPluginChannel(String name) {
        proxy.getChannelRegistrar().register(MinecraftChannelIdentifier.from(name));
    }

    @Override
    public boolean isProxyForwardingEnabled() {
        return true;
    }

    @Override
    public double getTPS() {
        return Double.NaN;
    } // A proxy has no server tick rate.

    @Override
    public void forwardAlert(String message) {
        /* AlertManager already reaches every subscribed proxy player. */
    }

    @Override
    public void applyValidationBlock(
            PlatformPlayer player, int x, int y, int z, String state, boolean packetOnly, Runnable applied) {
        throw new UnsupportedOperationException("A proxy cannot mutate server blocks");
    }
}
