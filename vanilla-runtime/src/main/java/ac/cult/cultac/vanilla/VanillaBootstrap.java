package ac.cult.cultac.vanilla;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

/** Loads vanilla model data without constructing a server, client, or live world. */
public final class VanillaBootstrap implements AutoCloseable {
    private static boolean started;
    private final com.google.common.collect.Interner<VanillaTagBindings<?>> tagBindings =
            com.google.common.collect.Interners.newWeakInterner();
    private final com.google.common.collect.Interner<VanillaComponentBindings<?>> componentBindings =
            com.google.common.collect.Interners.newWeakInterner();
    private final MultiPackResourceManager resources;
    private final RegistryAccess.Frozen registries;
    private final VanillaRegistryState.Bindings defaults;
    private final ThreadLocal<VanillaRegistryState> current = new ThreadLocal<>();
    private VanillaRegistryState.Bindings active;
    private boolean closed;

    public static synchronized VanillaBootstrap open() {
        if (started) throw new IllegalStateException("Vanilla model already started in this class loader");
        var bootstrap = new VanillaBootstrap();
        started = true;
        return bootstrap;
    }

    private VanillaBootstrap() {
        SharedConstants.tryDetectVersion();
        if (SharedConstants.getProtocolVersion() != 777
                || !SharedConstants.getCurrentVersion().id().equals("26.3")) {
            throw new IllegalStateException("Expected vanilla 26.3/protocol 777");
        }
        // Bootstrap normally installs Minecraft's process-wide console wrappers.
        // This is a library model inside a host application, which owns its streams.
        var out = System.out;
        var err = System.err;
        try {
            Bootstrap.bootStrap();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        resources = new MultiPackResourceManager(
                PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
        try {
            var base = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            TagLoader.loadTagsForExistingRegistries(resources, base).forEach(Registry.PendingTags::apply);
            var dynamic = RegistryDataLoader.load(
                            resources,
                            base.listRegistries().toList(),
                            RegistryDataLoader.WORLD_REGISTRIES,
                            Runnable::run)
                    .join();
            registries = new RegistryAccess.ImmutableRegistryAccess(
                            Stream.concat(base.registries(), dynamic.registries()))
                    .freeze();
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(pending -> pending.apply());
            defaults = VanillaRegistryState.snapshot(this, registries);
            active = defaults;
        } catch (RuntimeException | Error failure) {
            resources.close();
            throw failure;
        }
    }

    public RegistryAccess.Frozen registries() {
        return registries;
    }

    public MultiPackResourceManager resources() {
        return resources;
    }

    VanillaTagBindings<?> canonicalTags(VanillaTagBindings<?> tags) {
        return tagBindings.intern(tags);
    }

    VanillaComponentBindings<?> canonicalComponents(VanillaComponentBindings<?> components) {
        return componentBindings.intern(components);
    }

    public synchronized VanillaRegistryState newConnection() {
        if (closed) throw new IllegalStateException("Vanilla model is closed");
        return new VanillaRegistryState(this, defaults);
    }

    boolean isActive(VanillaRegistryState state) {
        return current.get() == state;
    }

    void activate(VanillaRegistryState state) {
        if (!Thread.holdsLock(this) || !isActive(state)) throw new IllegalStateException("Model context is not owned");
        if (active != state.bindings()) {
            state.bindings().apply(active);
            active = state.bindings();
        }
    }

    synchronized void execute(VanillaRegistryState state, Runnable task) {
        if (closed) throw new IllegalStateException("Vanilla model is closed");
        VanillaRegistryState previous = current.get();
        current.set(state);
        try {
            activate(state);
            task.run();
        } finally {
            if (previous == null) current.remove();
            else {
                current.set(previous);
                activate(previous);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            resources.close();
        }
    }
}
