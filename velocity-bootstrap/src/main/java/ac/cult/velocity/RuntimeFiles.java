package ac.cult.velocity;

import ac.cult.runtime.VanillaFiles;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

/** Extracts Cult's own engine and acquires the official model at runtime. */
final class RuntimeFiles {
    static URL[] prepare(Path directory) throws IOException, InterruptedException {
        var model = VanillaFiles.client(directory.resolve("runtime"));
        Path root = model.jar().getParent();
        Path engine = root.resolve("cult-engine.jar");
        Path temporary = Files.createTempFile(root, "engine-", ".tmp");
        try {
            try (var input = RuntimeFiles.class.getResourceAsStream("/runtime/cult-engine.jar")) {
                if (input == null) throw new IOException("Missing Cult engine");
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, engine, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        var urls = new ArrayList<URL>();
        urls.add(engine.toUri().toURL());
        urls.add(model.jar().toUri().toURL());
        for (var library : model.libraries()) urls.add(library.toUri().toURL());
        return urls.toArray(URL[]::new);
    }
}
