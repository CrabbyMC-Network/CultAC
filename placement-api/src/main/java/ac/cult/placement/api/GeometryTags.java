package ac.cult.placement.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Named tag membership for the three registries used by block placement and geometry. */
public record GeometryTags(
        Map<String, List<String>> blocks, Map<String, List<String>> items, Map<String, List<String>> fluids) {
    public GeometryTags {
        blocks = copy(blocks);
        items = copy(items);
        fluids = copy(fluids);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> source) {
        return source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
