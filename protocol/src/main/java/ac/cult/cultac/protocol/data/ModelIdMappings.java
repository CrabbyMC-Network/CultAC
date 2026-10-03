package ac.cult.cultac.protocol.data;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolResolutionException;
import ac.cult.cultac.protocol.ProtocolVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/** Composed ViaVersion numeric mappings, including vanilla identifier and property renames. */
public final class ModelIdMappings {
    private record Versions(ProtocolVersion source, ProtocolVersion target) {}

    private static final Map<Versions, ModelIdMappings> CACHE = new ConcurrentHashMap<>();
    private final Map<String, int[]> ids;

    private ModelIdMappings(Map<String, int[]> ids) {
        this.ids = Map.copyOf(ids);
    }

    /** Source must be older than target; newer content cannot be inverted by guessing a fallback. */
    public static ModelIdMappings load(ProtocolVersion source, ProtocolVersion target) {
        if (source.protocol() >= target.protocol())
            throw new IllegalArgumentException("Expected an older source protocol");
        return CACHE.computeIfAbsent(new Versions(source, target), ModelIdMappings::read);
    }

    /** Explicit directed projection, including the actual ViaBackwards client-visible fallbacks. */
    public static ModelIdMappings project(ProtocolVersion source, ProtocolVersion target) {
        if (source == target) throw new IllegalArgumentException("Identity projection needs no mapping table");
        return CACHE.computeIfAbsent(new Versions(source, target), ModelIdMappings::read);
    }

    private static ModelIdMappings read(Versions versions) {
        String path = "/ac/cult/cultac/protocol/model-mappings/" + versions.source.protocol() + "-to-"
                + versions.target.protocol() + ".tsv.gz";
        try (var resource = ModelIdMappings.class.getResourceAsStream(path)) {
            if (resource == null) throw new ProtocolResolutionException("Missing composed mappings " + versions);
            try (var reader =
                    new BufferedReader(new InputStreamReader(new GZIPInputStream(resource), StandardCharsets.UTF_8))) {
                String suffix = "\t" + versions.source.protocol() + "\t" + versions.target.protocol();
                String header = reader.readLine();
                boolean blocks = ("cult-via-model-mappings\t2" + suffix).equals(header);
                if (!blocks && !("cult-via-model-mappings\t1" + suffix).equals(header))
                    throw new ProtocolResolutionException("Wrong composed mapping versions");
                var rows = new HashMap<String, ArrayList<Integer>>();
                for (String line; (line = reader.readLine()) != null; ) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 3
                            || !(fields[0].equals("blockstates")
                                    || fields[0].equals("items")
                                    || blocks && fields[0].equals("blocks")))
                        throw new ProtocolResolutionException("Invalid composed mapping row");
                    var values = rows.computeIfAbsent(fields[0], ignored -> new ArrayList<>());
                    int source = Integer.parseInt(fields[1]), target = Integer.parseInt(fields[2]);
                    if (source != values.size() || target < (blocks && fields[0].equals("blocks") ? -1 : 0))
                        throw new ProtocolResolutionException("Invalid composed mapping ID");
                    values.add(target);
                }
                if (rows.size() != (blocks ? 3 : 2) || rows.values().stream().anyMatch(java.util.List::isEmpty))
                    throw new ProtocolResolutionException("Incomplete composed mappings");
                var ids = new HashMap<String, int[]>();
                rows.forEach((key, values) ->
                        ids.put(key, values.stream().mapToInt(Integer::intValue).toArray()));
                return new ModelIdMappings(ids);
            }
        } catch (IOException | NumberFormatException failure) {
            throw new ProtocolResolutionException("Cannot read composed mappings: " + failure.getMessage());
        }
    }

    public int blockState(int sourceId) {
        return mapped("blockstates", sourceId);
    }

    public int item(int sourceId) {
        return mapped("items", sourceId);
    }

    public int block(int sourceId) {
        return mapped("blocks", sourceId);
    }

    private int mapped(String key, int id) {
        var values = ids.get(key);
        if (values == null) throw new ProtocolResolutionException("Missing directed " + key + " mappings");
        if (id < 0 || id >= values.length) throw new MalformedPacketException("Unknown source " + key + " ID " + id);
        return values[id];
    }

    public int blockStateCount() {
        return ids.get("blockstates").length;
    }

    public int itemCount() {
        return ids.get("items").length;
    }

    public int blockCount() {
        return ids.get("blocks").length;
    }
}
