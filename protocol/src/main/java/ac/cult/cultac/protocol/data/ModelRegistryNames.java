package ac.cult.cultac.protocol.data;

import ac.cult.cultac.protocol.ProtocolResolutionException;
import ac.cult.cultac.protocol.ProtocolVersion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Registry identifier renames proven by the composed numeric mappings. */
public final class ModelRegistryNames {
    private final Map<String, String> blocks;
    private final Map<String, String> items;
    private final Map<String, String> hostItems;
    private final Set<String> removedBlocks;

    private ModelRegistryNames(ModelRegistryData source, ModelRegistryData target) {
        var blockNames = new HashMap<String, String>();
        var itemNames = new HashMap<String, String>();
        if (source.version() != target.version()) {
            var mappings = ModelIdMappings.load(source.version(), target.version());
            for (int id = 0; id < source.blockStates().size(); id++) {
                String name = blockName(source.blockStateName(id));
                if (target.registry("minecraft:block").id(name) >= 0) continue;
                String mapped = blockName(target.blockStateName(mappings.blockState(id)));
                String previous = blockNames.put(name, mapped);
                if (previous != null && !previous.equals(mapped))
                    throw new ProtocolResolutionException("Block identifier mapping is ambiguous: " + name);
            }
            var sourceItems = source.registry("minecraft:item");
            var targetItems = target.registry("minecraft:item");
            for (int id = 0; id < sourceItems.size(); id++) {
                String name = sourceItems.name(id);
                if (targetItems.id(name) < 0) itemNames.put(name, targetItems.name(mappings.item(id)));
            }
        }
        blocks = Map.copyOf(blockNames);
        items = Map.copyOf(itemNames);
        var reverse = new HashMap<String, String>();
        items.forEach((oldName, newName) -> {
            if (source.registry("minecraft:item").id(newName) >= 0 || reverse.put(newName, oldName) != null)
                throw new ProtocolResolutionException("Item identifier mapping cannot be inverted: " + newName);
        });
        hostItems = Map.copyOf(reverse);
        removedBlocks = Set.of();
    }

    public static ModelRegistryNames load(ProtocolVersion source, ProtocolVersion target) {
        return new ModelRegistryNames(ModelRegistryData.load(source), ModelRegistryData.load(target));
    }

    /** Native newer values projected to an older client model using actual backward mappings. */
    public static ModelRegistryNames project(ProtocolVersion source, ProtocolVersion target) {
        if (source.protocol() <= target.protocol()) return load(source, target);
        return new ModelRegistryNames(
                ModelRegistryData.load(source),
                ModelRegistryData.load(target),
                ModelIdMappings.project(source, target),
                ModelIdMappings.load(target, source));
    }

    private ModelRegistryNames(
            ModelRegistryData source, ModelRegistryData target, ModelIdMappings toModel, ModelIdMappings toHost) {
        var sourceBlocks = source.registry("minecraft:block");
        var targetBlocks = target.registry("minecraft:block");
        var sourceItems = source.registry("minecraft:item");
        var targetItems = target.registry("minecraft:item");
        if (toModel.blockCount() != sourceBlocks.size()
                || toModel.itemCount() != sourceItems.size()
                || toHost.itemCount() != targetItems.size())
            throw new ProtocolResolutionException("Directed registry mapping does not match its registry");
        var blockNames = new HashMap<String, String>();
        var itemNames = new HashMap<String, String>();
        var hostNames = new HashMap<String, String>();
        var removed = new HashSet<String>();
        for (int id = 0; id < sourceBlocks.size(); id++) {
            int mapped = toModel.block(id);
            if (mapped == -1) removed.add(sourceBlocks.name(id));
            else blockNames.put(sourceBlocks.name(id), targetBlocks.name(mapped));
        }
        for (int id = 0; id < sourceItems.size(); id++)
            itemNames.put(sourceItems.name(id), targetItems.name(toModel.item(id)));
        for (int id = 0; id < targetItems.size(); id++)
            hostNames.put(targetItems.name(id), sourceItems.name(toHost.item(id)));
        blocks = Map.copyOf(blockNames);
        items = Map.copyOf(itemNames);
        hostItems = Map.copyOf(hostNames);
        removedBlocks = Set.copyOf(removed);
    }

    private static String blockName(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    /** null means ViaBackwards removes this tag member; it is not a substitute block. */
    public String modelBlock(String name) {
        return removedBlocks.contains(name) ? null : blocks.getOrDefault(name, name);
    }

    public String modelItem(String name) {
        return items.getOrDefault(name, name);
    }

    public String hostItem(String name) {
        return hostItems.getOrDefault(name, name);
    }
}
