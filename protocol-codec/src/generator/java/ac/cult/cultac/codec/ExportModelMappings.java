package ac.cult.cultac.codec;

import ac.cult.cultac.protocol.data.ModelRegistryData;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.protocol.ProtocolPathEntry;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Build tool only: exports actual directed ViaBackwards mappings, never an inverted fallback. */
public final class ExportModelMappings {
    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments[0]);
        Files.createDirectories(output);
        var manager = ModernProtocols.open(
                Files.createTempDirectory("cult-mapping-export-").toFile());
        try {
            // 777 -> 776 maps the 26.3 model to the 26.2 Java client Geyser speaks on a proxy.
            for (int[] pair : List.of(new int[] {776, 774}, new int[] {777, 774}, new int[] {777, 776})) {
                int source = pair[0], target = pair[1];
                var path = manager.getProtocolManager()
                        .getProtocolPath(ProtocolVersion.getProtocol(target), ProtocolVersion.getProtocol(source));
                if (path == null || path.isEmpty()) throw new IllegalStateException("Missing backward path");
                var clientbound = new ArrayList<>(path);
                Collections.reverse(clientbound);
                var old = ModelRegistryData.load(ac.cult.cultac.protocol.ProtocolVersion.of(source));
                var model = ModelRegistryData.load(ac.cult.cultac.protocol.ProtocolVersion.of(target));
                try (var writer = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(
                                Files.newOutputStream(output.resolve(source + "-to-" + target + ".tsv.gz"))),
                        StandardCharsets.UTF_8))) {
                    writer.write("cult-via-model-mappings\t2\t" + source + "\t" + target + "\n");
                    table(
                            writer,
                            "blockstates",
                            old.blockStates().size(),
                            model.blockStates().size(),
                            clientbound);
                    table(
                            writer,
                            "blocks",
                            old.registry("minecraft:block").size(),
                            model.registry("minecraft:block").size(),
                            clientbound);
                    table(
                            writer,
                            "items",
                            old.registry("minecraft:item").size(),
                            model.registry("minecraft:item").size(),
                            clientbound);
                }
                System.out.println("EXPORTED " + source + " -> " + target + " "
                        + clientbound.stream()
                                .map(edge -> edge.protocol().getClass().getSimpleName())
                                .toList());
            }
        } finally {
            manager.destroy();
        }
    }

    private static void table(
            BufferedWriter writer, String key, int count, int targetCount, List<ProtocolPathEntry> edges)
            throws Exception {
        for (int source = 0; source < count; source++) {
            int mapped = source;
            for (var edge : edges) {
                // TagRewriter removes a block member at -1; subsequent edges never see it.
                if (mapped == -1 && key.equals("blocks")) break;
                MappingData data = edge.protocol().getMappingData();
                if (data == null) continue;
                mapped = switch (key) {
                    case "blockstates" ->
                        data.getBlockStateMappings() == null ? mapped : data.getNewBlockStateId(mapped);
                    case "blocks" -> data.getBlockMappings() == null ? mapped : data.getNewBlockId(mapped);
                    case "items" -> data.getItemMappings() == null ? mapped : data.getNewItemId(mapped);
                    default -> throw new IllegalArgumentException(key);
                };
            }
            if (mapped < (key.equals("blocks") ? -1 : 0) || mapped >= targetCount)
                throw new IllegalStateException("Invalid " + key + " mapping " + source + " -> " + mapped);
            writer.write(key + "\t" + source + "\t" + mapped + "\n");
        }
    }
}
