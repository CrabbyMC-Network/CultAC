package ac.cult.placement.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.CodeSource;
import java.security.SecureClassLoader;

/** Loaded by the JDK loader, so host plugin remappers cannot transform isolated definitions. */
public final class ClassDefiner {
    private ClassDefiner() {}

    public static MethodHandle forLoader(Class<?> loader) throws ReflectiveOperationException {
        var signature =
                MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class, CodeSource.class);
        return MethodHandles.privateLookupIn(loader, MethodHandles.lookup())
                .findVirtual(SecureClassLoader.class, "defineClass", signature)
                .asType(signature.insertParameterTypes(0, SecureClassLoader.class));
    }
}
