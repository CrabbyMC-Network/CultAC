package ac.cult.placement.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** JDK-only support for cache guards in the isolated vanilla classes. */
public final class ModelCaches {
    private ModelCaches() {}

    /** The caller holds the cache monitor until this snapshot is complete. */
    public static <T> Iterator<T> snapshotIterator(Collection<T> values) {
        return new ArrayList<>(values).iterator();
    }
}
