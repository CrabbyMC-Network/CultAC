package ac.cult.cultac.vanilla;

import ac.cult.cultac.utils.minecraft.MinecraftRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;

/** Client configuration data, including the static holder bindings vanilla keeps globally. */
public final class VanillaRegistryState {
    static final class Bindings {
        private final Map<ResourceKey<? extends Registry<?>>, VanillaTagBindings<?>> tags;
        private final Map<ResourceKey<? extends Registry<?>>, VanillaComponentBindings<?>> components;
        private ac.cult.placement.api.GeometryTags geometryTags;

        Bindings(List<VanillaTagBindings<?>> tags, java.util.Collection<VanillaComponentBindings<?>> components) {
            Map<ResourceKey<? extends Registry<?>>, VanillaTagBindings<?>> byRegistry = new HashMap<>();
            tags.forEach(pending -> byRegistry.put(pending.key(), pending));
            this.tags = Map.copyOf(byRegistry);
            Map<ResourceKey<? extends Registry<?>>, VanillaComponentBindings<?>> byComponentRegistry = new HashMap<>();
            components.forEach(pending -> byComponentRegistry.put(pending.key(), pending));
            this.components = Map.copyOf(byComponentRegistry);
        }

        java.util.Collection<VanillaTagBindings<?>> tags() {
            return tags.values();
        }

        java.util.Collection<VanillaComponentBindings<?>> components() {
            return components.values();
        }

        void apply(Bindings previous) {
            tags.forEach((key, pending) -> {
                if (previous.tags.get(key) != pending) pending.apply();
            });
            components.forEach((key, pending) -> {
                var old = previous.components.get(key);
                if (old != pending) pending.applyAfter(old);
            });
            boolean unchanged = tags.get(Registries.BLOCK) == previous.tags.get(Registries.BLOCK)
                    && tags.get(Registries.ITEM) == previous.tags.get(Registries.ITEM)
                    && tags.get(Registries.FLUID) == previous.tags.get(Registries.FLUID);
            if (unchanged && previous.geometryTags != null) geometryTags = previous.geometryTags;
            if (geometryTags == null) geometryTags = ac.cult.cultac.utils.minecraft.NativeGeometryTags.capture();
            ac.cult.cultac.utils.minecraft.IsolatedMinecraft.tags(geometryTags);
        }
    }

    private final VanillaBootstrap model;
    private final Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> entries =
            new HashMap<>();
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags =
            new HashMap<>();
    private RegistryAccess.Frozen registries;
    private Bindings bindings;
    private final MinecraftRegistries context;

    VanillaRegistryState(VanillaBootstrap model, Bindings bindings) {
        this.model = model;
        this.registries = model.registries();
        this.bindings = bindings;
        this.context = new MinecraftRegistries(() -> registries, model::resources);
    }

    public MinecraftRegistries context() {
        return context;
    }

    Bindings bindings() {
        return bindings;
    }

    public void execute(Runnable task) {
        model.execute(this, task);
    }

    public boolean isActive() {
        return model.isActive(this);
    }

    public void append(
            ResourceKey<? extends Registry<?>> key, List<RegistrySynchronization.PackedRegistryEntry> values) {
        requireOwner();
        entries.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(values);
    }

    public void appendTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> values) {
        requireOwner();
        tags.putAll(values);
    }

    /** PLAY applies even empty tag payloads, unlike the configuration collector. */
    public Runnable preparePlayTags(
            Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> values) {
        requireOwner();
        Map<ResourceKey<? extends Registry<?>>, VanillaTagBindings<?>> incoming = new HashMap<>();
        values.forEach((key, payload) -> incoming.put(key, prepare(registries.lookupOrThrow(key), payload)));
        return () -> {
            requireOwner();
            Map<ResourceKey<? extends Registry<?>>, VanillaTagBindings<?>> updated = new HashMap<>();
            bindings.tags().forEach(previous -> updated.put(previous.key(), previous));
            updated.putAll(incoming);
            bindings = new Bindings(List.copyOf(updated.values()), bindings.components());
            model.activate(this);
        };
    }

    /** Matches RegistryDataCollector: resolve tags, load received registries, then initialize defaults. */
    public void finish() {
        requireOwner();
        List<VanillaTagBindings<?>> pending = snapshotTags(model, registries);
        Map<ResourceKey<? extends Registry<?>>, VanillaTagBindings<?>> replacements = new HashMap<>();
        Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> network = new HashMap<>();
        entries.forEach((key, values) -> network.put(
                key,
                new RegistryDataLoader.NetworkedRegistryData(
                        List.copyOf(values), TagNetworkSerialization.NetworkPayload.EMPTY)));
        tags.forEach((key, payload) -> {
            if (payload.isEmpty()) return; // The vanilla collector also preserves bindings for empty payloads.
            if (!entries.isEmpty() && RegistrySynchronization.isNetworkable(key)) {
                network.compute(
                        key,
                        (ignored, previous) -> new RegistryDataLoader.NetworkedRegistryData(
                                previous == null ? List.of() : previous.elements(), payload));
            } else {
                replacements.put(key, prepare(registries.lookupOrThrow(key), payload));
            }
        });
        pending.replaceAll(previous -> replacements.getOrDefault(previous.key(), previous));
        if (!entries.isEmpty()) {
            var base = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var staticTags = pending.stream()
                    .filter(value -> base.lookup(value.key()).isPresent())
                    .toList();
            var received = RegistryDataLoader.load(
                            network,
                            model.resources(),
                            TagLoader.buildUpdatedLookups(base, new ArrayList<>(staticTags)),
                            RegistryDataLoader.SYNCHRONIZED_REGISTRIES,
                            Runnable::run)
                    .join();
            registries = new RegistryAccess.ImmutableRegistryAccess(
                            Stream.concat(base.registries(), received.registries()))
                    .freeze();
            pending = staticTags;
        }
        bindings = new Bindings(List.copyOf(pending), snapshotComponents(model, registries));
        entries.clear();
        tags.clear();
        model.activate(this);
    }

    private void requireOwner() {
        if (!isActive()) throw new IllegalStateException("Registry mutation outside its model context");
    }

    static Bindings snapshot(VanillaBootstrap model, RegistryAccess access) {
        return new Bindings(snapshotTags(model, access), snapshotComponents(model, access));
    }

    private static List<VanillaComponentBindings<?>> snapshotComponents(VanillaBootstrap model, RegistryAccess access) {
        return BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(access).stream()
                .<VanillaComponentBindings<?>>map(
                        pending -> model.canonicalComponents(new VanillaComponentBindings<>(pending)))
                .toList();
    }

    private static List<VanillaTagBindings<?>> snapshotTags(VanillaBootstrap model, RegistryAccess access) {
        List<VanillaTagBindings<?>> result = new ArrayList<>();
        access.registries().forEach(entry -> result.add(model.canonicalTags(snapshotTags(entry.value()))));
        return result;
    }

    private static <T> VanillaTagBindings<T> snapshotTags(Registry<T> registry) {
        Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
        registry.getTags().forEach(tag -> tags.put(tag.key(), tag.stream().toList()));
        return new VanillaTagBindings<>(registry, new TagLoader.LoadResult<>(registry.key(), tags));
    }

    private <T> VanillaTagBindings<?> prepare(Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        return model.canonicalTags(new VanillaTagBindings<>(registry, payload.resolve(registry)));
    }
}
