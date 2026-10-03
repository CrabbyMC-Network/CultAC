package ac.cult.placement;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Packages the isolated model may take from the host instead of loading its own copy.
 *
 * <p>Only the model's third-party libraries qualify (never Minecraft classes, which a
 * server may patch), and only when the host resolves the library from a byte-identical
 * jar. A package spread over several jars is shared only if every one of them is.
 */
final class SharedLibraries {
    private SharedLibraries() {}

    static Set<String> packages(List<Path> libraries, ClassLoader host) throws IOException {
        var owners = new HashMap<String, List<Boolean>>();
        for (Path library : libraries) {
            boolean identical = identical(library, host);
            for (String pack : packagesOf(library))
                owners.computeIfAbsent(pack, k -> new ArrayList<>()).add(identical);
        }
        var shared = new HashSet<String>();
        owners.forEach((pack, identical) -> {
            if (!identical.contains(false)) shared.add(pack);
        });
        return Set.copyOf(shared);
    }

    private static Set<String> packagesOf(Path library) throws IOException {
        var packages = new HashSet<String>();
        try (var jar = new JarFile(library.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements(); ) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class"))
                    continue;
                int slash = name.lastIndexOf('/');
                if (slash > 0) packages.add(name.substring(0, slash).replace('/', '.'));
            }
        }
        return packages;
    }

    private static boolean identical(Path library, ClassLoader host) throws IOException {
        String sample = null;
        try (var jar = new JarFile(library.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements() && sample == null; ) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class"))
                    sample = name;
            }
        }
        if (sample == null) return false;
        Path hostJar;
        try {
            // Paper's plugin loader resolves classes through the server, but resource
            // lookup can prefer the plugin's own shaded copy or hide server libraries.
            // Check the jar that actually defines the class, without initializing it.
            Class<?> resolved =
                    Class.forName(sample.substring(0, sample.length() - 6).replace('/', '.'), false, host);
            var source = resolved.getProtectionDomain().getCodeSource();
            if (source == null || !"file".equals(source.getLocation().getProtocol())) return false;
            hostJar = Path.of(source.getLocation().toURI());
        } catch (ClassNotFoundException
                | LinkageError
                | SecurityException
                | URISyntaxException
                | IllegalArgumentException unusable) {
            return false;
        }
        if (!Files.isRegularFile(hostJar) || Files.size(hostJar) != Files.size(library)) return false;
        return Arrays.equals(sha256(hostJar), sha256(library));
    }

    private static byte[] sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) != -1; ) digest.update(buffer, 0, read);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
