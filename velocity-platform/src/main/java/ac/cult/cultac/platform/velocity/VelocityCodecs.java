package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.protocol.PacketProjectionService;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

/** Private Via codecs and resources; external Via plugins retain their own state and mappings. */
final class VelocityCodecs implements AutoCloseable {
    private final CodecLoader loader;
    private final PacketProjectionService service;

    VelocityCodecs(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path jar = directory.resolve("protocol-codecs.jar");
        Path temporary = Files.createTempFile(directory, "codecs-", ".tmp");
        try {
            try (var input = getClass().getResourceAsStream("/runtime/protocol-codecs.jar")) {
                if (input == null) throw new IOException("Missing private packet codecs");
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        loader = new CodecLoader(jar.toUri().toURL(), getClass().getClassLoader());
        try {
            service = (PacketProjectionService) loader.loadClass("ac.cult.cultac.codec.PrivateCodecService")
                    .getConstructor(Path.class)
                    .newInstance(directory);
        } catch (Exception | Error failure) {
            loader.close();
            throw failure;
        }
    }

    PacketProjectionService service() {
        return service;
    }

    @Override
    public void close() throws IOException {
        try {
            service.close();
        } finally {
            loader.close();
        }
    }

    static final class CodecLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }

        CodecLoader(URL jar, ClassLoader parent) {
            super(new URL[] {jar}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = ownClass(name) ? findClass(name) : super.loadClass(name, false);
                if (resolve) resolveClass(type);
                return type;
            }
        }

        @Override
        public URL getResource(String name) {
            return ownResource(name) ? findResource(name) : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return ownResource(name) ? findResources(name) : super.getResources(name);
        }

        private static boolean ownClass(String name) {
            return name.startsWith("com.viaversion.") || name.startsWith("ac.cult.cultac.codec.");
        }

        private static boolean ownResource(String name) {
            return name.startsWith("assets/viaversion/")
                    || name.startsWith("assets/viabackwards/")
                    || name.startsWith("com/viaversion/")
                    || name.startsWith("ac/cult/cultac/codec/");
        }
    }
}
