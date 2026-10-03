package ac.cult.cultac.vanilla;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;

/** Exact defaults for one registry, including the identity of every receiving holder. */
final class VanillaComponentBindings<T> {
    private final DataComponentInitializers.PendingComponents<T> pending;
    private final Map<Holder.Reference<T>, DataComponentMap> contents;
    private final List<Binding<T>> entries;
    // Values refer only to this binding's holders/components, never the previous
    // context. Weak keys and a bounded cache cannot retain disconnected clients.
    // Activation already holds VanillaBootstrap's model lock.
    private final Map<VanillaComponentBindings<?>, List<Binding<T>>> transitions = new WeakHashMap<>();
    private final int hash;

    VanillaComponentBindings(DataComponentInitializers.PendingComponents<T> pending) {
        this.pending = pending;
        Map<Holder.Reference<T>, DataComponentMap> copied = new HashMap<>();
        pending.forEach(copied::put);
        contents = Map.copyOf(copied);
        entries = contents.entrySet().stream()
                .map(entry -> new Binding<>(entry.getKey(), entry.getValue()))
                .toList();
        hash = 31 * pending.key().hashCode() + contents.hashCode();
    }

    ResourceKey<? extends Registry<? extends T>> key() {
        return pending.key();
    }

    void apply() {
        pending.apply();
    }

    void applyAfter(VanillaComponentBindings<?> previous) {
        if (previous == null) {
            apply();
            return;
        }
        List<Binding<T>> changed = transitions.get(previous);
        if (changed == null) {
            changed = entries.stream()
                    .filter(entry -> !entry.components().equals(previous.contents.get(entry.holder())))
                    .toList();
            if (changed.size() == entries.size()) changed = entries;
            if (transitions.size() >= 64) transitions.clear();
            transitions.put(previous, changed);
        }
        // Vanilla PendingComponents.apply only invokes Holder.bindComponents;
        // that method assigns the map. Equal maps need no repeat assignment.
        changed.forEach(entry -> entry.holder().bindComponents(entry.components()));
    }

    private record Binding<T>(Holder.Reference<T> holder, DataComponentMap components) {}

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof VanillaComponentBindings<?> that
                        && key().equals(that.key())
                        && contents.equals(that.contents);
    }
}
