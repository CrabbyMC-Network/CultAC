package ac.cult.runtime;

import java.nio.file.Path;

/** Build-time acquisition uses the same pinned files and verification as production. */
public final class AcquireServer {
    private AcquireServer() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Expected model version and cache directory");
        var model = RuntimeModel.forVersion(arguments[0]);
        var files = VanillaFiles.server(Path.of(arguments[1]), model);
        System.out.println(
                "Verified " + model.minecraftId() + " and " + files.libraries().size() + " libraries");
    }
}
