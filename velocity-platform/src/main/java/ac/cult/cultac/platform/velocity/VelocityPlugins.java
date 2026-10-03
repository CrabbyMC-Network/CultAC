package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.platform.api.PlatformPlugin;
import ac.cult.cultac.platform.api.manager.PlatformPluginManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Locale;

final class VelocityPlugins implements PlatformPluginManager {
    private final ProxyServer proxy;

    VelocityPlugins(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public PlatformPlugin[] getPlugins() {
        return proxy.getPluginManager().getPlugins().stream().map(Entry::new).toArray(PlatformPlugin[]::new);
    }

    @Override
    public PlatformPlugin getPlugin(String name) {
        return proxy.getPluginManager()
                .getPlugin(name.toLowerCase(Locale.ROOT))
                .or(() -> proxy.getPluginManager().getPlugins().stream()
                        .filter(plugin ->
                                plugin.getDescription().getName().orElse("").equalsIgnoreCase(name))
                        .findFirst())
                .map(Entry::new)
                .orElse(null);
    }

    private record Entry(PluginContainer plugin) implements PlatformPlugin {
        @Override
        public boolean isEnabled() {
            return plugin.getInstance().isPresent();
        }

        @Override
        public String getName() {
            return plugin.getDescription()
                    .getName()
                    .orElse(plugin.getDescription().getId());
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion().orElse("unknown");
        }
    }
}
