package ac.cult.cultac.bedrock.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.registry.type.BlockMappings;

/** Geyser moved GameProtocol and shades the custom-block map's return type in platform jars. */
final class GeyserBlockMappingsAccess {
    private static final Method CUSTOM_BLOCK_STATES = method(BlockMappings.class, "getCustomBlockStateDefinitions");

    private GeyserBlockMappingsAccess() {}

    static int javaProtocolVersion() {
        return (int) invoke(JavaProtocol.ACCESSOR, null);
    }

    @SuppressWarnings("unchecked")
    static Map<CustomBlockState, ? extends BlockDefinition> customBlockStates(BlockMappings mappings) {
        // Both the shaded and unshaded fastutil interfaces implement java.util.Map.
        return (Map<CustomBlockState, ? extends BlockDefinition>) invoke(CUSTOM_BLOCK_STATES, mappings);
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Unsupported Geyser API: " + type.getName() + "." + name, failure);
        }
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Geyser API: " + method, failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Geyser API failed: " + method, cause);
        }
    }

    private static final class JavaProtocol {
        private static final Method ACCESSOR = resolve();

        private static Method resolve() {
            ClassLoader loader = BlockMappings.class.getClassLoader();
            for (String name : new String[] {
                "org.geysermc.geyser.network.bedrock.GameProtocol", "org.geysermc.geyser.network.GameProtocol"
            }) {
                try {
                    return method(Class.forName(name, false, loader), "getJavaProtocolVersion");
                } catch (ClassNotFoundException ignored) {
                    // The pinned core and current platform distribution use different packages.
                }
            }
            throw new IllegalStateException("Unsupported Geyser API: missing GameProtocol");
        }
    }
}
