package ac.cult.cultac.network.packet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

/** The six native light values; older hosts expose getters and newer hosts expose record accessors. */
public record LightValues(
        BitSet skyYMask,
        BitSet blockYMask,
        BitSet emptySkyYMask,
        BitSet emptyBlockYMask,
        List<byte[]> skyUpdates,
        List<byte[]> blockUpdates) {
    private static final List<Method> ACCESSORS =
            List.of("skyYMask", "blockYMask", "emptySkyYMask", "emptyBlockYMask", "skyUpdates", "blockUpdates").stream()
                    .map(LightValues::accessor)
                    .toList();

    private static Method accessor(String name) {
        try {
            try {
                return ClientboundLightUpdatePacketData.class.getMethod(name);
            } catch (NoSuchMethodException earlier) {
                return ClientboundLightUpdatePacketData.class.getMethod(
                        "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
        } catch (NoSuchMethodException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @SuppressWarnings("unchecked")
    public static LightValues fromNative(ClientboundLightUpdatePacketData data) {
        try {
            return new LightValues(
                    (BitSet) ACCESSORS.get(0).invoke(data),
                    (BitSet) ACCESSORS.get(1).invoke(data),
                    (BitSet) ACCESSORS.get(2).invoke(data),
                    (BitSet) ACCESSORS.get(3).invoke(data),
                    (List<byte[]>) ACCESSORS.get(4).invoke(data),
                    (List<byte[]>) ACCESSORS.get(5).invoke(data));
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to read native light values", cause);
        }
    }
}
