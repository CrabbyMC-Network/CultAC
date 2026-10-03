package ac.cult.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

/** Acquires unmodified, version-pinned Mojang artifacts into a persistent runtime cache. */
public final class VanillaFiles {
    public record Model(Path jar, List<Path> libraries) {
        public Model {
            libraries = List.copyOf(libraries);
        }
    }

    @FunctionalInterface
    interface Download {
        InputStream open(URI uri) throws IOException, InterruptedException;
    }

    private VanillaFiles() {}

    public static Model client(Path cache) throws IOException, InterruptedException {
        return prepare(cache, false);
    }

    public static Model server(Path cache) throws IOException, InterruptedException {
        return server(cache, RuntimeModel.JAVA_26_3);
    }

    public static Model server(Path cache, RuntimeModel model) throws IOException, InterruptedException {
        return prepare(cache, true, model);
    }

    private static Model prepare(Path cache, boolean server) throws IOException, InterruptedException {
        return prepare(cache, server, RuntimeModel.JAVA_26_3);
    }

    private static Model prepare(Path cache, boolean server, RuntimeModel model)
            throws IOException, InterruptedException {
        var lock = new Properties();
        try (var input = VanillaFiles.class.getResourceAsStream("/vanilla/" + model.version() + ".properties")) {
            if (input == null) throw new IOException("Missing vanilla acquisition manifest");
            lock.load(input);
        }
        // A valid cache needs no network; only build the client (and its TLS stack) to download.
        var client = new HttpClient[1];
        try {
            return prepare(cache, server, lock, uri -> {
                if (client[0] == null)
                    client[0] = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(20))
                            .build();
                var request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build();
                var response = client[0].send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("Vanilla download returned HTTP " + response.statusCode() + ": " + uri);
                }
                return response.body();
            });
        } finally {
            if (client[0] != null) client[0].close();
        }
    }

    // Also serializes different callers sharing the same data directory in this JVM.
    static synchronized Model prepare(Path cache, boolean server, Properties lock, Download download)
            throws IOException, InterruptedException {
        RuntimeModel model;
        try {
            model = RuntimeModel.forVersion(required(lock, "version"));
        } catch (IllegalArgumentException unsupported) {
            throw new IOException("Unexpected vanilla model", unsupported);
        }
        if (!Integer.toString(model.protocol()).equals(lock.getProperty("protocol")))
            throw new IOException("Unexpected vanilla version in acquisition manifest");
        Path root = cache.toAbsolutePath().normalize().resolve(model.version());
        Files.createDirectories(root);
        try (var channel = FileChannel.open(
                        root.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var ignored = channel.lock()) {
            if (server) {
                Path bundle = install(root.resolve("server-bundle.jar"), lock, "server", download);
                return unpackServer(root, bundle, model);
            }
            Path clientModel = install(root.resolve("client.jar"), lock, "client", download);
            var libraries = new ArrayList<Path>();
            int count = Integer.parseInt(required(lock, "libraries"));
            for (int index = 0; index < count; index++) {
                String key = "library." + index;
                Path path = safePath(root.resolve("lib"), required(lock, key + ".path"));
                libraries.add(install(path, lock, key, download));
            }
            return new Model(clientModel, libraries);
        }
    }

    private static Path install(Path target, Properties lock, String key, Download download)
            throws IOException, InterruptedException {
        URI uri = URI.create(required(lock, key + ".url"));
        if (!"https".equals(uri.getScheme())
                || uri.getUserInfo() != null
                || uri.getPort() != -1
                || !List.of("piston-data.mojang.com", "libraries.minecraft.net").contains(uri.getHost()))
            throw new IOException("Unexpected vanilla artifact origin: " + uri);
        String hash = required(lock, key + ".sha1");
        long size = Long.parseLong(required(lock, key + ".size"));
        if (valid(target, "SHA-1", hash, size)) return target;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".download-", ".tmp");
        try {
            try (var input = download.open(uri)) {
                copy(input, temporary, size);
            }
            if (!valid(temporary, "SHA-1", hash, size)) throw new IOException("Vanilla checksum mismatch: " + uri);
            replace(temporary, target);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Model unpackServer(Path root, Path bundle, RuntimeModel expectedModel) throws IOException {
        var libraries = new ArrayList<Path>();
        Path model = null;
        try (var archive = new JarFile(bundle.toFile(), false)) {
            for (String kind : List.of("versions", "libraries")) {
                var listing = archive.getJarEntry("META-INF/" + kind + ".list");
                if (listing == null) throw new IOException("Missing Mojang bundle manifest: " + kind);
                String text;
                try (var input = archive.getInputStream(listing)) {
                    text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                for (String line :
                        text.lines().filter(value -> !value.isBlank()).toList()) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 3) throw new IOException("Invalid Mojang bundle manifest");
                    boolean version = kind.equals("versions");
                    if (version && !expectedModel.minecraftId().equals(fields[1]))
                        throw new IOException("Mojang bundle model does not match " + expectedModel.minecraftId());
                    if (!version && fields[2].contains("/log4j-slf4j2-impl/")) continue;
                    safePath(root, fields[2]);
                    var entry = archive.getJarEntry("META-INF/" + kind + "/" + fields[2]);
                    if (entry == null || entry.isDirectory() || entry.getSize() < 0)
                        throw new IOException("Missing Mojang bundle entry: " + fields[2]);
                    Path target = version
                            ? root.resolve("server-model.jar")
                            : safePath(root.resolve("server-lib"), fields[2]);
                    if (version && model != null) throw new IOException("Multiple models in Mojang bundle");
                    if (!valid(target, "SHA-256", fields[0], entry.getSize())) {
                        Files.createDirectories(target.getParent());
                        Path temporary = Files.createTempFile(target.getParent(), ".extract-", ".tmp");
                        try {
                            try (var input = archive.getInputStream(entry)) {
                                copy(input, temporary, entry.getSize());
                            }
                            if (!valid(temporary, "SHA-256", fields[0], entry.getSize()))
                                throw new IOException("Mojang bundle checksum mismatch: " + fields[2]);
                            replace(temporary, target);
                        } finally {
                            Files.deleteIfExists(temporary);
                        }
                    }
                    if (version) model = target;
                    else libraries.add(target);
                }
            }
        }
        if (model == null) throw new IOException("Mojang bundle has no vanilla model");
        return new Model(model, libraries);
    }

    private static String required(Properties lock, String key) throws IOException {
        String value = lock.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing acquisition metadata: " + key);
        return value;
    }

    private static Path safePath(Path root, String name) throws IOException {
        Path relative = Path.of(name);
        Path target = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !target.startsWith(root) || target.equals(root))
            throw new IOException("Invalid vanilla artifact path: " + name);
        return target;
    }

    private static void copy(InputStream input, Path target, long size) throws IOException {
        long count = 0;
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1; ) {
                count += read;
                if (count > size) throw new IOException("Vanilla artifact exceeds pinned size");
                output.write(buffer, 0, read);
            }
        }
    }

    private static boolean valid(Path path, String algorithm, String expected, long size) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != size) return false;
        try {
            var digest = MessageDigest.getInstance(algorithm);
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest()).equals(expected);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
