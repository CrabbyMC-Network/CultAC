package ac.cult.cultac.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Identifies translated Bedrock sessions; they are admitted only with a Geyser bridge tap on this proxy.
 */
final class VelocityClientSupport {
    private record Lookup(Method singleton, Method lookup, boolean booleanResult) {
        boolean contains(UUID player) {
            try {
                Object result = lookup.invoke(singleton.invoke(null), player);
                return booleanResult ? Boolean.TRUE.equals(result) : result != null;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot identify translated client", failure);
            }
        }
    }

    private final List<Lookup> lookups = new ArrayList<>();

    VelocityClientSupport(ProxyServer proxy) {
        add(proxy, "geyser", "org.geysermc.geyser.api.GeyserApi", "api", "connectionByUuid", false);
        add(proxy, "floodgate", "org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer", true);
    }

    private void add(
            ProxyServer proxy, String plugin, String api, String singleton, String lookup, boolean booleanResult) {
        proxy.getPluginManager().getPlugin(plugin).ifPresent(container -> {
            Object instance = container.getInstance().orElseThrow();
            try {
                Class<?> type = Class.forName(api, false, instance.getClass().getClassLoader());
                lookups.add(new Lookup(type.getMethod(singleton), type.getMethod(lookup, UUID.class), booleanResult));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unsupported " + plugin + " API", failure);
            }
        });
    }

    boolean isBedrock(UUID player) {
        if (player.toString().startsWith("00000000-0000-0000-0009")) {
            return true;
        }
        return lookups.stream().anyMatch(lookup -> lookup.contains(player));
    }
}
