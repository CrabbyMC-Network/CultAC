package ac.cult.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaFilesTest {
    @TempDir
    Path cache;

    @Test
    void clientDownloadsOnceReusesVerifiedCacheAndRepairsCorruption() throws Exception {
        byte[] client = "client-fixture".getBytes(), library = "library-fixture".getBytes();
        var lock = lock("client", client);
        lock.setProperty("libraries", "1");
        artifact(lock, "library.0", library, "https://libraries.minecraft.net/fixture.jar");
        lock.setProperty("library.0.path", "fixture/library.jar");
        var downloads = new AtomicInteger();
        VanillaFiles.Download source = uri -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(uri.getHost().equals("libraries.minecraft.net") ? library : client);
        };
        var model = VanillaFiles.prepare(cache, false, lock, source);
        assertEquals(2, downloads.get());
        assertArrayEquals(client, Files.readAllBytes(model.jar()));
        assertArrayEquals(library, Files.readAllBytes(model.libraries().getFirst()));
        VanillaFiles.prepare(cache, false, lock, offline());
        Files.write(model.jar(), "client-corrupt".getBytes()); // Same length: hash checking is necessary.
        VanillaFiles.prepare(cache, false, lock, source);
        assertEquals(3, downloads.get());
        assertArrayEquals(client, Files.readAllBytes(model.jar()));
    }

    @Test
    void serverDownloadsBundleAndVerifiesExtractedModelAndLibrariesOnEveryStart() throws Exception {
        byte[] model = "model-fixture".getBytes(), library = "library-fixture".getBytes();
        byte[] bundle = bundle(model, library, "fixture/library.jar", false);
        var lock = lock("server", bundle);
        var downloads = new AtomicInteger();
        var prepared = VanillaFiles.prepare(cache, true, lock, uri -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(bundle);
        });
        assertEquals(1, downloads.get());
        assertArrayEquals(model, Files.readAllBytes(prepared.jar()));
        assertEquals(1, prepared.libraries().size());
        Files.write(prepared.jar(), "model-corrupt".getBytes());
        Files.write(prepared.libraries().getFirst(), "library-corrupt".getBytes());
        var repaired = VanillaFiles.prepare(cache, true, lock, offline());
        assertArrayEquals(model, Files.readAllBytes(repaired.jar()));
        assertArrayEquals(library, Files.readAllBytes(repaired.libraries().getFirst()));
        assertEquals(prepared, repaired);
        assertThrows(
                UnsupportedOperationException.class, () -> repaired.libraries().clear());
    }

    @Test
    void checksumFailureCannotCommitADownloadAndCleansTemporaryFiles() throws Exception {
        byte[] expected = "expected".getBytes();
        var lock = lock("client", expected);
        assertThrows(
                IOException.class,
                () -> VanillaFiles.prepare(cache, false, lock, uri -> new ByteArrayInputStream("tampered".getBytes())));
        assertFalse(Files.exists(cache.resolve("26.3/client.jar")));
        assertNoTemporaryFiles();
    }

    @Test
    void oversizedAndTruncatedDownloadsFailBeforePublishing() throws Exception {
        var lock = lock("client", "expected".getBytes());
        for (String content : new String[] {"far-too-large", "short"}) {
            assertThrows(
                    IOException.class,
                    () -> VanillaFiles.prepare(
                            cache, false, lock, uri -> new ByteArrayInputStream(content.getBytes())));
            assertFalse(Files.exists(cache.resolve("26.3/client.jar")));
            assertNoTemporaryFiles();
        }
    }

    @Test
    void unexpectedOriginsNeverReachTheDownloader() throws Exception {
        for (String url : new String[] {
            "http://piston-data.mojang.com/client.jar",
            "https://example.com/client.jar",
            "https://user@piston-data.mojang.com/client.jar",
            "https://piston-data.mojang.com:444/client.jar"
        }) {
            var lock = lock("client", new byte[0]);
            lock.setProperty("client.url", url);
            assertThrows(IOException.class, () -> VanillaFiles.prepare(cache, false, lock, offline()));
        }
    }

    @Test
    void libraryPathsCannotEscapeTheCache() throws Exception {
        byte[] fixture = "fixture".getBytes();
        var lock = lock("client", fixture);
        lock.setProperty("libraries", "1");
        artifact(lock, "library.0", fixture, "https://libraries.minecraft.net/fixture.jar");
        for (String path :
                new String[] {"../../escape.jar", cache.resolve("escape.jar").toString()}) {
            lock.setProperty("library.0.path", path);
            assertThrows(
                    IOException.class,
                    () -> VanillaFiles.prepare(cache, false, lock, uri -> new ByteArrayInputStream(fixture)));
            assertFalse(Files.exists(cache.resolve("escape.jar")));
        }
    }

    @Test
    void bundleChecksumsAndPathsAreVerifiedBeforeExtractionIsPublished() throws Exception {
        byte[] model = "model".getBytes(), library = "library".getBytes();
        for (boolean badHash : new boolean[] {false, true}) {
            String path = badHash ? "fixture/library.jar" : "../../escape.jar";
            byte[] bundle = bundle(model, library, path, badHash);
            assertThrows(
                    IOException.class,
                    () -> VanillaFiles.prepare(
                            cache, true, lock("server", bundle), uri -> new ByteArrayInputStream(bundle)));
            assertFalse(Files.exists(cache.resolve("escape.jar")));
            assertFalse(Files.exists(cache.resolve("26.3/server-lib/fixture/library.jar")));
            assertNoTemporaryFiles();
        }
    }

    @Test
    void versionCannotDriftToAnUnpinnedModel() throws Exception {
        var lock = lock("client", new byte[0]);
        lock.setProperty("version", "26.4");
        assertThrows(IOException.class, () -> VanillaFiles.prepare(cache, false, lock, offline()));
    }

    @Test
    void olderServerModelsUseASeparateVerifiedCacheAndRequireTheNamedArtifact() throws Exception {
        byte[] bundle = bundle("model".getBytes(), "library".getBytes(), "library.jar", false, "1.21.11_unobfuscated");
        var lock = lock("server", bundle);
        lock.setProperty("version", "1.21.11");
        lock.setProperty("protocol", "774");
        var model = VanillaFiles.prepare(cache, true, lock, uri -> new ByteArrayInputStream(bundle));
        assertEquals(cache.resolve("1.21.11/server-model.jar"), model.jar());
        assertFalse(Files.exists(cache.resolve("26.3")));
        assertEquals(model, VanillaFiles.prepare(cache, true, lock, offline()));

        byte[] wrong = bundle("model".getBytes(), "library".getBytes(), "library.jar", false, "1.21.11");
        artifact(lock, "server", wrong, "https://piston-data.mojang.com/fixture.jar");
        assertThrows(
                IOException.class,
                () -> VanillaFiles.prepare(cache, true, lock, uri -> new ByteArrayInputStream(wrong)),
                "An obfuscated 1.21.11 artifact cannot replace the named model");
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.walk(cache)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }

    private static VanillaFiles.Download offline() {
        return uri -> {
            throw new AssertionError("Unexpected network access: " + uri);
        };
    }

    private static Properties lock(String kind, byte[] data) throws Exception {
        var lock = new Properties();
        lock.setProperty("version", "26.3");
        lock.setProperty("protocol", "777");
        lock.setProperty("libraries", "0");
        artifact(lock, kind, data, "https://piston-data.mojang.com/fixture.jar");
        return lock;
    }

    private static void artifact(Properties lock, String key, byte[] data, String url) throws Exception {
        lock.setProperty(key + ".sha1", hash("SHA-1", data));
        lock.setProperty(key + ".size", Integer.toString(data.length));
        lock.setProperty(key + ".url", url);
    }

    private static String hash(String algorithm, byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
    }

    private static byte[] bundle(byte[] model, byte[] library, String path, boolean badHash) throws Exception {
        return bundle(model, library, path, badHash, "26.3");
    }

    private static byte[] bundle(byte[] model, byte[] library, String path, boolean badHash, String version)
            throws Exception {
        var output = new ByteArrayOutputStream();
        try (var archive = new JarOutputStream(output)) {
            entry(
                    archive,
                    "META-INF/versions.list",
                    (hash("SHA-256", model) + "\t" + version + "\t26.3/model.jar\n").getBytes());
            entry(archive, "META-INF/versions/26.3/model.jar", model);
            String hash = badHash ? "0".repeat(64) : hash("SHA-256", library);
            entry(archive, "META-INF/libraries.list", (hash + "\tfixture:library:1\t" + path + "\n").getBytes());
            entry(archive, "META-INF/libraries/" + path, library);
        }
        return output.toByteArray();
    }

    private static void entry(JarOutputStream archive, String name, byte[] data) throws IOException {
        archive.putNextEntry(new JarEntry(name));
        archive.write(data);
        archive.closeEntry();
    }
}
