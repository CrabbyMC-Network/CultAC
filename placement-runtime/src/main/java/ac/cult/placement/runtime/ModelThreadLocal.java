package ac.cult.placement.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Per-thread vanilla caches owned by the model. A ThreadLocalMap value in a live
 * packet thread would otherwise keep a retired model's classes reachable.
 */
public final class ModelThreadLocal<T> extends ThreadLocal<T> {
    private final Map<Thread, T> values = new WeakHashMap<>();
    private final Supplier<? extends T> initial;

    private ModelThreadLocal(Supplier<? extends T> initial) {
        this.initial = Objects.requireNonNull(initial);
    }

    public static <T> ThreadLocal<T> withInitial(Supplier<? extends T> initial) {
        return new ModelThreadLocal<>(initial);
    }

    @Override
    public synchronized T get() {
        Thread thread = Thread.currentThread();
        T value = values.get(thread);
        if (value == null && !values.containsKey(thread)) {
            value = initial.get();
            values.put(thread, value);
        }
        return value;
    }

    @Override
    public synchronized void set(T value) {
        values.put(Thread.currentThread(), value);
    }

    @Override
    public synchronized void remove() {
        values.remove(Thread.currentThread());
    }
}
