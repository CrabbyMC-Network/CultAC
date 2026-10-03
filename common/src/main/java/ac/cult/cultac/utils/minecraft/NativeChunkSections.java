package ac.cult.cultac.utils.minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Constructs the host's original section reader across the palette-factory API change. */
public final class NativeChunkSections {
    private static final Constructor<LevelChunkSection> CONSTRUCTOR;
    private static final Method CREATE_PALETTES;
    private static volatile Palettes palettes;

    static {
        try {
            Class<?> factory;
            try {
                factory = Class.forName("net.minecraft.world.level.chunk.PalettedContainerFactory");
            } catch (ClassNotFoundException earlier) {
                factory = null;
            }
            CONSTRUCTOR = LevelChunkSection.class.getConstructor(factory == null ? Registry.class : factory);
            CREATE_PALETTES = factory == null ? null : factory.getMethod("create", RegistryAccess.class);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private NativeChunkSections() {}

    public static LevelChunkSection create(RegistryAccess access) {
        try {
            Palettes current = palettes;
            if (current == null || current.access() != access) {
                Object value = CREATE_PALETTES == null
                        ? access.lookupOrThrow(Registries.BIOME)
                        : CREATE_PALETTES.invoke(null, access);
                current = new Palettes(access, value);
                palettes = current;
            }
            return CONSTRUCTOR.newInstance(current.value());
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Native section initialization failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native section API unavailable", failure);
        }
    }

    private record Palettes(RegistryAccess access, Object value) {}
}
