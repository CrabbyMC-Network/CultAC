package ac.cult.placement;

import ac.cult.placement.api.BlockGeometry;
import ac.cult.placement.api.GeometryTags;
import ac.cult.placement.api.InteractionEngine;
import ac.cult.placement.api.PlacementEngine;
import ac.cult.runtime.RuntimeModel;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** One shared model; actions share a read lock, while tags and model lifetime are exclusive. */
public final class PlacementRuntime implements BlockGeometry, AutoCloseable {
    private final Path directory;
    private final Path workerJar;
    private final Path cache;
    private final RuntimeModel model;
    private InteractionRuntime interactions;
    private GeometryTags lastTags;
    private final ReentrantReadWriteLock access = new ReentrantReadWriteLock(true);
    private final AtomicBoolean recovering = new AtomicBoolean();
    private final boolean parallel;
    private ArchiveLoader loader;
    private PlacementEngine engine;

    public static boolean available() {
        return available(RuntimeModel.JAVA_26_3);
    }

    public static boolean available(RuntimeModel model) {
        return PlacementRuntime.class.getResource("/vanilla/" + model.version() + ".properties") != null;
    }

    public PlacementRuntime(Path workerJar) throws Exception {
        this(workerJar, workerJar.toAbsolutePath().getParent().resolve("runtime"), false, RuntimeModel.JAVA_26_3);
    }

    /** Production uses one original vanilla model for geometry and interactions. */
    public static PlacementRuntime openVanilla(Path workerJar) throws Exception {
        return openVanilla(workerJar, workerJar.toAbsolutePath().getParent().resolve("runtime"));
    }

    public static PlacementRuntime openVanilla(Path workerJar, Path cache) throws Exception {
        return openVanilla(workerJar, cache, RuntimeModel.JAVA_26_3);
    }

    public static PlacementRuntime openVanilla(Path workerJar, Path cache, RuntimeModel model) throws Exception {
        return new PlacementRuntime(workerJar, cache, true, Objects.requireNonNull(model));
    }

    public RuntimeModel model() {
        return model;
    }

    private PlacementRuntime(Path workerJar, Path cache, boolean vanilla, RuntimeModel model) throws Exception {
        this.workerJar = workerJar;
        this.cache = cache;
        this.model = model;
        // The 1.21.11 cache paths have not undergone the 26.3 concurrency audit.
        parallel = vanilla && model == RuntimeModel.JAVA_26_3;
        if (vanilla) {
            directory = null;
            interactions = openInteractions(workerJar, cache, model);
            engine = interactions.geometry();
            return;
        }
        String audit = System.getProperty("placementGeometryProfile");
        if (audit == null)
            throw new IOException("Compact geometry is a local audit profile; production must use openVanilla");
        directory = Path.of(audit).toAbsolutePath().normalize();
        try {
            try (var in = Files.newInputStream(directory.resolve("engine.jar"))) {
                var digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                for (int read; (read = in.read(buffer)) != -1; ) digest.update(buffer, 0, read);
                if (!HexFormat.of()
                        .formatHex(digest.digest())
                        .equals("7e2ba06b59d57c08c6b97fd4215170f6dc2192293683e962a63721de761844c4")) {
                    throw new IOException("Unexpected placement engine version");
                }
            }
            var roots = new ArrayList<URL>();
            roots.add(directory.resolve("helpers.jar").toUri().toURL());
            roots.add(directory.resolve("engine.jar").toUri().toURL());
            roots.add(directory.resolve("adapter.jar").toUri().toURL());
            roots.add(workerJar.toUri().toURL());
            try (var files = Files.list(directory.resolve("lib"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".jar"))
                        .sorted()
                        .toList()) roots.add(file.toUri().toURL());
            }
            loader = new ArchiveLoader(roots.toArray(URL[]::new), PlacementEngine.class.getClassLoader());
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader == null ? interactions.classLoader() : loader);
                engine = (PlacementEngine) Class.forName("bench.bridge.CompensatedEngine", true, loader)
                        .getConstructor()
                        .newInstance();
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
            loader.releaseArchiveIndexes();
        } catch (Exception | Error failure) {
            try {
                close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public int stateCount() {
        return query(null, false, () -> engine.stateCount());
    }

    public void tags(GeometryTags tags) {
        Objects.requireNonNull(tags);
        exclusive();
        try {
            requireOpen();
            installTags(tags);
        } finally {
            access.writeLock().unlock();
        }
    }

    public String stateName(int state) {
        return query(null, false, () -> engine.stateName(state));
    }

    @Override
    public BlockGeometry.State state(int id) {
        return query(null, false, () -> engine.state(id));
    }

    @Override
    public List<PlacementEngine.Box> shape(
            PlacementEngine.World world, PlacementEngine.Pos pos, int state, Shape kind, Context context) {
        return query(world.tags(), false, () -> engine.shape(world, pos, state, kind, context));
    }

    public List<PlacementEngine.Box> collision(PlacementEngine.World world, PlacementEngine.Pos pos) {
        return query(world.tags(), false, () -> engine.collision(world, pos));
    }

    public List<PlacementEngine.Box> outline(PlacementEngine.World world, PlacementEngine.Pos pos) {
        return query(world.tags(), false, () -> engine.outline(world, pos));
    }

    public PlacementEngine.Result place(PlacementEngine.Request request) {
        return query(request.world().tags(), false, () -> engine.place(request));
    }

    private void requireOpen() {
        if (engine == null) throw new IllegalStateException("Placement runtime is closed");
    }

    private void installTags(GeometryTags tags) {
        if (tags == lastTags || tags.equals(lastTags)) return;
        try {
            engine.tags(tags);
            if (loader != null && interactions != null) interactions.geometry().tags(tags);
            // Record only a successfully installed snapshot, including per-world tags.
            lastTags = tags;
        } catch (RuntimeException | Error failure) {
            narrowingFailed(interactions, failure);
            throw failure;
        } finally {
            release();
        }
    }

    public InteractionEngine.Result interact(InteractionEngine.Request request) {
        return query(request.world().tags(), true, () -> interactions.interact(request));
    }

    /** Obtain the correct tag generation, downgrading the writer before running an action. */
    private Lock enter(GeometryTags tags, boolean interaction) {
        // The compact profile is only a local audit input; keep its excluded-path counters serialized.
        Lock read = parallel ? access.readLock() : access.writeLock();
        read.lock();
        try {
            requireOpen();
            if ((!interaction || interactions != null) && (tags == null || tags == lastTags || tags.equals(lastTags)))
                return read;
        } catch (RuntimeException | Error failure) {
            read.unlock();
            throw failure;
        }
        read.unlock();
        exclusive();
        try {
            requireOpen();
            if (interaction) prepareInteractionsLocked();
            if (tags != null) installTags(tags);
            read.lock(); // A downgrade: a queued tag writer cannot overtake this request.
            return read;
        } finally {
            access.writeLock().unlock();
        }
    }

    private <T> T query(GeometryTags tags, boolean interaction, Supplier<T> operation) {
        Lock read = enter(tags, interaction);
        var previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader == null ? interactions.classLoader() : loader);
            return operation.get();
        } catch (RuntimeException | Error failure) {
            narrowingFailed(interactions, failure);
            throw failure;
        } finally {
            try {
                Thread.currentThread().setContextClassLoader(previous);
                release();
            } finally {
                read.unlock();
            }
        }
    }

    /** A reader cannot upgrade its lock from inside a world callback. */
    private void exclusive() {
        if (access.getReadHoldCount() != 0 && !access.isWriteLockedByCurrentThread())
            throw new IllegalStateException("Cannot change the vanilla model or its tags during an active action");
        access.writeLock().lock();
    }

    public void prepareInteractions() {
        exclusive();
        try {
            requireOpen();
            prepareInteractionsLocked();
        } finally {
            access.writeLock().unlock();
        }
    }

    private void prepareInteractionsLocked() {
        requireOpen();
        if (interactions == null) {
            InteractionRuntime opened = null;
            try {
                opened = new InteractionRuntime(workerJar, cache, model, false);
                if (lastTags != null) opened.geometry().tags(lastTags);
                opened.releaseArchiveIndexes();
                interactions = opened;
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to open vanilla interaction runtime", failure);
            } finally {
                if (opened != null && opened != interactions) {
                    try {
                        opened.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /** Whether the narrowed client model is serving requests (false after a fallback). */
    public boolean narrowed() {
        access.readLock().lock();
        try {
            return interactions != null && interactions.narrowed;
        } finally {
            access.readLock().unlock();
        }
    }

    private static final System.Logger LOGGER = System.getLogger("CultAC");

    /** The narrowed model when it starts, else the full one; startup is off the packet path. */
    private static InteractionRuntime openInteractions(Path workerJar, Path cache, RuntimeModel model)
            throws Exception {
        if (model != RuntimeModel.JAVA_26_3) return new InteractionRuntime(workerJar, cache, model, false);
        try {
            return new InteractionRuntime(workerJar, cache, model, true);
        } catch (Exception | Error failure) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "The narrowed vanilla model failed to start; using the full model",
                    failure);
            return new InteractionRuntime(workerJar, cache, model, false);
        }
    }

    /**
     * What leaving content out can cause: a class that fails to link or initialize (such as
     * one registering into a skipped, frozen registry), a data registry the model did not
     * load, or a failure inside the narrowing support itself. Invalid requests fail the same
     * way in both models and do not count.
     */
    private static boolean fromNarrowing(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof LinkageError) return true;
            if (cause instanceof IllegalStateException state
                    && (String.valueOf(state.getMessage()).contains("already frozen")
                            || String.valueOf(state.getMessage()).startsWith("Missing registry"))) return true;
            for (var frame : cause.getStackTrace())
                if (frame.getClassName().startsWith("ac.cult.placement.runtime.ModelScope")) return true;
        }
        return false;
    }

    /**
     * A narrowed model that throws may have reached something it leaves out. Report it once and
     * build the full model in the background (about 3 s, never on a packet thread), then swap.
     */
    private void narrowingFailed(InteractionRuntime failed, Throwable failure) {
        if (failed == null || !failed.narrowed || !fromNarrowing(failure) || !recovering.compareAndSet(false, true))
            return;
        LOGGER.log(
                System.Logger.Level.WARNING, "The narrowed vanilla model failed; switching to the full model", failure);
        var recovery = Thread.ofPlatform()
                .name("cult-vanilla-full-model")
                .daemon()
                .inheritInheritableThreadLocals(false)
                .unstarted(() -> {
                    InteractionRuntime full = null;
                    try {
                        full = new InteractionRuntime(workerJar, cache, model, false);
                        access.writeLock().lock();
                        try {
                            if (engine == null || interactions != failed) return;
                            // Install the latest successful tags before publishing the replacement.
                            if (lastTags != null) full.geometry().tags(lastTags);
                            full.releaseArchiveIndexes();
                            interactions = full;
                            engine = full.geometry();
                            full = null;
                            try {
                                failed.close();
                            } catch (Exception closing) {
                                LOGGER.log(System.Logger.Level.WARNING, "Unable to close the narrowed model", closing);
                            }
                        } finally {
                            access.writeLock().unlock();
                        }
                    } catch (Exception | Error unavailable) {
                        LOGGER.log(System.Logger.Level.ERROR, "Unable to open the full vanilla model", unavailable);
                    } finally {
                        if (full != null) {
                            try {
                                full.close();
                            } catch (Exception closing) {
                                LOGGER.log(
                                        System.Logger.Level.WARNING, "Unable to close the unused full model", closing);
                            }
                        }
                        recovering.set(false);
                    }
                });
        // Failures are detected while the caller's context loader is the isolated one.
        // A background builder must not inherit and retain that retiring loader.
        recovery.setContextClassLoader(PlacementRuntime.class.getClassLoader());
        recovery.start();
    }

    private void release() {
        try {
            if (loader != null) loader.releaseArchiveIndexes();
            if (interactions != null) interactions.releaseArchiveIndexes();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    @Override
    public void close() throws IOException {
        exclusive();
        try {
            closeLocked();
        } finally {
            access.writeLock().unlock();
        }
    }

    private void closeLocked() throws IOException {
        IOException interactionFailure = null;
        if (interactions != null) {
            try {
                interactions.close();
            } catch (Exception failure) {
                interactionFailure = new IOException("Unable to close interaction runtime", failure);
            } finally {
                interactions = null;
            }
        }
        engine = null;
        lastTags = null;
        try {
            if (loader != null) loader.close();
        } finally {
            loader = null;
        }
        if (interactionFailure != null) throw interactionFailure;
    }
}
