package ac.cult.placement;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Our tiny JDK-only definer stays outside Paper's host-NMS reflection rewriting. */
final class ModelDefinitions {
    private ModelDefinitions() {}

    static synchronized MethodHandle open(Class<?> loader, Path cache) throws Exception {
        byte[] bytes;
        try (var input = ModelDefinitions.class.getResourceAsStream("/runtime/cult-class-definer.jar")) {
            if (input == null) throw new IOException("Missing Cult class definer");
            bytes = input.readAllBytes();
        }
        String hash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Files.createDirectories(cache);
        Path jar = cache.resolve("cult-class-definer-" + hash + ".jar");
        if (!Files.isRegularFile(jar)
                || Files.size(jar) != bytes.length
                || !java.util.Arrays.equals(Files.readAllBytes(jar), bytes)) {
            Path temporary = Files.createTempFile(cache, "cult-class-definer-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        // Only this one class and JDK types are in the child. Close its archive immediately.
        try (var child =
                new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            return (MethodHandle) Class.forName("ac.cult.placement.runtime.ClassDefiner", true, child)
                    .getMethod("forLoader", Class.class)
                    .invoke(null, loader);
        }
    }
}
