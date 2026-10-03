package ac.cult.cultac.utils.minecraft;

import ac.cult.cultac.protocol.data.ModelRegistryNames;
import ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil;
import ac.cult.placement.api.GeometryTags;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Captures the active native bindings using values safe to cross the isolated loader. */
public final class NativeGeometryTags {
    private NativeGeometryTags() {}

    public static GeometryTags capture() {
        return new GeometryTags(
                capture(BuiltInRegistries.BLOCK, UnaryOperator.identity()),
                capture(BuiltInRegistries.ITEM, UnaryOperator.identity()),
                capture(BuiltInRegistries.FLUID, UnaryOperator.identity()));
    }

    public static GeometryTags capture(ModelRegistryNames names) {
        return new GeometryTags(
                capture(BuiltInRegistries.BLOCK, names::modelBlock),
                capture(BuiltInRegistries.ITEM, names::modelItem),
                capture(BuiltInRegistries.FLUID, UnaryOperator.identity()));
    }

    public static GeometryTags translate(GeometryTags tags, ModelRegistryNames names) {
        return new GeometryTags(
                translate(tags.blocks(), names::modelBlock), translate(tags.items(), names::modelItem), tags.fluids());
    }

    private static Map<String, List<String>> translate(Map<String, List<String>> tags, UnaryOperator<String> names) {
        Map<String, List<String>> translated = new HashMap<>();
        tags.forEach((tag, members) -> translated.put(
                tag,
                members.stream().map(names).filter(java.util.Objects::nonNull).toList()));
        return translated;
    }

    private static <T> Map<String, List<String>> capture(Registry<T> registry, UnaryOperator<String> names) {
        Map<String, List<String>> tags = new HashMap<>();
        registry.getTags()
                .forEach(tag -> tags.put(
                        NmsIdentifierUtil.tagKey(tag.key()),
                        tag.stream()
                                .map(holder -> names.apply(NmsIdentifierUtil.registryKey(registry, holder.value())))
                                .filter(java.util.Objects::nonNull)
                                .toList()));
        return tags;
    }
}
