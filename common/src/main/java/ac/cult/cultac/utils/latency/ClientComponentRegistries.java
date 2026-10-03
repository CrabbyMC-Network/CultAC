package ac.cult.cultac.utils.latency;

import ac.cult.cultac.protocol.data.ModelRegistryNames;
import ac.cult.cultac.utils.minecraft.MinecraftRegistries;
import ac.cult.cultac.utils.minecraft.NativeGeometryTags;
import ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

/**
 * The block, item and fluid tags a client received, as named memberships for host-side checks.
 * The vanilla runtime reads no client registries: interactions transfer only item type data.
 */
public final class ClientComponentRegistries {
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags =
            new HashMap<>();
    private ac.cult.placement.api.GeometryTags geometryTags;
    private final Map<ModelRegistryNames, ac.cult.placement.api.GeometryTags> modelTags =
            new java.util.IdentityHashMap<>();

    public void appendTags(ac.cult.cultac.network.packet.RegistryTags packet) {
        if (packet.tags().entrySet().stream().allMatch(entry -> entry.getValue().equals(tags.get(entry.getKey()))))
            return;
        tags.putAll(packet.tags());
        geometryTags = null;
        modelTags.clear();
    }

    public ac.cult.placement.api.GeometryTags geometryTags(MinecraftRegistries context) {
        if (geometryTags == null)
            geometryTags = new ac.cult.placement.api.GeometryTags(
                    exportTags(
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK,
                            tags.get(net.minecraft.core.registries.Registries.BLOCK)),
                    exportTags(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM,
                            tags.get(net.minecraft.core.registries.Registries.ITEM)),
                    exportTags(
                            net.minecraft.core.registries.BuiltInRegistries.FLUID,
                            tags.get(net.minecraft.core.registries.Registries.FLUID)));
        return geometryTags;
    }

    /** One snapshot per model and received generation; tag names and empty memberships stay intact. */
    public ac.cult.placement.api.GeometryTags geometryTags(MinecraftRegistries context, ModelRegistryNames names) {
        return modelTags.computeIfAbsent(names, key -> NativeGeometryTags.translate(geometryTags(context), key));
    }

    private static <T> Map<String, List<String>> exportTags(
            Registry<T> registry, TagNetworkSerialization.NetworkPayload payload) {
        var result = new HashMap<String, List<String>>();
        if (payload == null)
            registry.getTags()
                    .forEach(tag -> result.put(
                            NmsIdentifierUtil.resourceKey(tag.key()),
                            tag.stream()
                                    .map(holder -> NmsIdentifierUtil.registryKey(registry, holder.value()))
                                    .toList()));
        else
            payload.resolve(registry)
                    .tags()
                    .forEach((name, members) -> result.put(
                            NmsIdentifierUtil.tagKey(name),
                            members.stream()
                                    .map(holder -> NmsIdentifierUtil.registryKey(registry, holder.value()))
                                    .toList()));
        return Map.copyOf(result);
    }
}
