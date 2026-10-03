package ac.cult.placement;

import ac.cult.placement.api.InteractionEngine;
import ac.cult.runtime.RuntimeModel;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;

/** Official vanilla model, isolated from every host Minecraft class; see {@link VanillaClassTransform}. */
final class InteractionRuntime implements AutoCloseable {
    private ArchiveLoader loader;
    private InteractionEngine engine;

    final boolean narrowed;

    InteractionRuntime(Path workerJar, Path cache, boolean narrowed) throws Exception {
        this(workerJar, cache, RuntimeModel.JAVA_26_3, narrowed);
    }

    InteractionRuntime(Path workerJar, Path cache, RuntimeModel runtimeModel, boolean narrowed) throws Exception {
        if (narrowed && runtimeModel != RuntimeModel.JAVA_26_3)
            throw new IllegalArgumentException("The narrowing profile is specific to 26.3");
        this.narrowed = narrowed;
        try {
            var model = ac.cult.runtime.VanillaFiles.server(cache, runtimeModel);
            var roots = new ArrayList<URL>();
            roots.add(model.jar().toUri().toURL());
            roots.add(workerJar.toUri().toURL());
            for (var library : model.libraries()) roots.add(library.toUri().toURL());
            var host = InteractionEngine.class.getClassLoader();
            String bridgePrefix =
                    runtimeModel == RuntimeModel.JAVA_26_3 ? "" : "placement-models/" + runtimeModel.version() + "/";
            loader = new ArchiveLoader(
                    roots.toArray(URL[]::new),
                    host,
                    SharedLibraries.packages(model.libraries(), host),
                    narrowed,
                    bridgePrefix);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader);
                engine = (InteractionEngine)
                        Class.forName("ac.cult.vanilla.interaction.VanillaInteractions", true, loader)
                                .getConstructor(boolean.class)
                                .newInstance(narrowed);
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
            loader.releaseArchiveIndexes();
        } catch (Exception | Error failure) {
            try {
                close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    ac.cult.placement.api.PlacementEngine geometry() {
        return (ac.cult.placement.api.PlacementEngine) engine;
    }

    ClassLoader classLoader() {
        return loader;
    }

    void releaseArchiveIndexes() throws IOException {
        loader.releaseArchiveIndexes();
    }

    InteractionEngine.Result interact(InteractionEngine.Request request) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            return engine.interact(request);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (engine != null) engine.close();
        } finally {
            engine = null;
            try {
                if (loader != null) loader.close();
            } finally {
                loader = null;
            }
        }
    }
}
