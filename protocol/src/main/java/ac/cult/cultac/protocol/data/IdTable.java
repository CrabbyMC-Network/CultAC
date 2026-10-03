package ac.cult.cultac.protocol.data;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolResolutionException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable, contiguous vanilla wire IDs. Ordering is data, never enum ordinal. */
public final class IdTable {
    private final String description;
    private final List<String> names;
    private final Map<String, Integer> ids;

    public IdTable(String description, List<String> names) {
        this.description = description;
        this.names = List.copyOf(names);
        Map<String, Integer> ids = new HashMap<>();
        for (int id = 0; id < this.names.size(); id++) {
            String name = this.names.get(id);
            // Vanilla identifiers permit an empty path (for example minecraft:).
            // Version-specific identifier parsing belongs at the wire boundary.
            if (!name.matches("[a-z0-9_.-]*:[a-z0-9/._-]*") || ids.put(name, id) != null) {
                throw new ProtocolResolutionException("Invalid/duplicate name in " + description + ": " + name);
            }
        }
        this.ids = Map.copyOf(ids);
    }

    public int size() {
        return names.size();
    }

    public int id(String name) {
        return ids.getOrDefault(name, -1);
    }

    public String name(int id) {
        if (id < 0 || id >= names.size()) {
            throw new MalformedPacketException("Unknown " + description + " ID " + id);
        }
        return names.get(id);
    }

    public List<String> names() {
        return names;
    }
}
