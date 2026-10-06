package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.BlockUpdatesMessage;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.BlockMappings;

/** Captures named layer corrections only inside stock Java acknowledgement translation. */
final class GatewayBlockUpdates {
    private record Layer(Vector3i position, int layer) { }
    private final Map<Layer, String> changes = new LinkedHashMap<>();
    private final Map<Integer, String> palette;
    private int depth;
    GatewayBlockUpdates(BlockMappings mappings) { palette = palette(mappings); }
    void begin() { depth++; }
    void capture(BedrockPacket packet) {
        if (depth == 0) return;
        if (packet instanceof UpdateBlockPacket block) update(block.getBlockPosition(), block.getDataLayer(), block.getDefinition());
        else if (packet instanceof UpdateSubChunkBlocksPacket blocks) {
            for (var block : blocks.getStandardBlocks()) update(block.getPosition(), 0, block.getDefinition());
            for (var block : blocks.getExtraBlocks()) update(block.getPosition(), 1, block.getDefinition());
        }
    }
    List<BlockUpdatesMessage> end() {
        if (depth == 0) throw new IllegalStateException("Unbalanced native block acknowledgement");
        if (--depth != 0) return List.of();
        // Chunk per position so both layers are applied atomically at the same native receipt.
        var positions = new LinkedHashMap<Vector3i, List<BlockUpdatesMessage.Update>>();
        changes.forEach((key, value) -> positions.computeIfAbsent(key.position, p -> new ArrayList<>())
                .add(new BlockUpdatesMessage.Update(key.position.getX(), key.position.getY(), key.position.getZ(), key.layer, value)));
        changes.clear(); return positions.values().stream().map(BlockUpdatesMessage::new).toList();
    }
    void reset() { depth = 0; changes.clear(); }
    private void update(Vector3i pos, int layer, BlockDefinition definition) {
        String state = definition == null ? null : palette.get(definition.getRuntimeId());
        if (state == null) throw new IllegalArgumentException("Unmapped native correction definition");
        if (layer != 0 && layer != 1) throw new IllegalArgumentException("Unknown correction layer");
        changes.put(new Layer(pos, layer), state);
    }
    private static Map<Integer, String> palette(BlockMappings mappings) {
        var states = new HashMap<Integer, String>();
        var entries = new ArrayList<>(BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.get().object2IntEntrySet());
        entries.sort(Comparator.comparingInt(e -> e.getIntValue()));
        for (var entry : entries) {
            String state = entry.getKey().replace("waterlogged=true", "waterlogged=false");
            states.putIfAbsent(mappings.getBedrockBlock(entry.getIntValue()).getRuntimeId(), state);
            states.putIfAbsent(mappings.getVanillaBedrockBlock(entry.getIntValue()).getRuntimeId(), state);
        }
        states.put(mappings.getBedrockAir().getRuntimeId(), "minecraft:air");
        if (mappings.getItemFrames() != null) mappings.getItemFrames().values().forEach(d -> states.putIfAbsent(d.getRuntimeId(), "minecraft:air"));
        for (var skull : BlockRegistries.CUSTOM_SKULLS.get().values()) {
            for (int rotation = 0; rotation < 16; rotation++) {
                var definition = mappings.getCustomBlockStateDefinitions().get(skull.getFloorBlockState(rotation));
                if (definition != null) states.put(definition.getRuntimeId(), "minecraft:player_head[rotation=" + rotation + "]");
            }
            for (var facing : List.of("south", "west", "north", "east")) {
                int angle = switch (facing) { case "south" -> 0; case "west" -> 90; case "north" -> 180; default -> 270; };
                var definition = mappings.getCustomBlockStateDefinitions().get(skull.getWallBlockState(angle));
                if (definition != null) states.put(definition.getRuntimeId(), "minecraft:player_wall_head[facing=" + facing + "]");
            }
        }
        return Map.copyOf(states);
    }
}
