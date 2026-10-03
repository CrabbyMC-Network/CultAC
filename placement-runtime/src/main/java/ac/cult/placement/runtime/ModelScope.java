package ac.cult.placement.runtime;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the narrowed client model leaves out, called from transformed vanilla classes.
 *
 * <p>Each skipped built-in registry holds content only a server, world generator or UI
 * reads; none is reachable from client-side block/item actions, and none is defaulted,
 * synchronized, or referenced by codecs the model decodes. Living entities' default
 * attributes are built on first use instead of for all 93 mob types at once.
 */
public final class ModelScope {
    static final Set<String> SKIPPED_REGISTRIES = Set.of(
            "minecraft:rule_test",
            "minecraft:rule_block_entity_modifier",
            "minecraft:pos_rule_test",
            "minecraft:command_argument_type",
            "minecraft:height_provider_type",
            "minecraft:worldgen/carver_type",
            "minecraft:worldgen/feature_type",
            "minecraft:worldgen/structure_placement",
            "minecraft:worldgen/structure_piece",
            "minecraft:worldgen/structure_type",
            "minecraft:worldgen/placement_modifier_type",
            "minecraft:worldgen/foliage_placer_type",
            "minecraft:worldgen/trunk_placer_type",
            "minecraft:worldgen/root_placer_type",
            "minecraft:worldgen/tree_decorator_type",
            "minecraft:worldgen/feature_size_type",
            "minecraft:worldgen/biome_source",
            "minecraft:worldgen/chunk_generator",
            "minecraft:worldgen/material_condition_type",
            "minecraft:worldgen/material_rule_type",
            "minecraft:worldgen/density_function_type",
            "minecraft:worldgen/structure_processor",
            "minecraft:worldgen/structure_pool_element",
            "minecraft:worldgen/pool_alias_binding",
            "minecraft:creative_mode_tab",
            "minecraft:number_format_type",
            "minecraft:recipe_serializer",
            "minecraft:recipe_display",
            "minecraft:slot_display",
            "minecraft:recipe_book_category",
            "minecraft:ticket_type",
            "minecraft:incoming_rpc_methods",
            "minecraft:outgoing_rpc_methods");

    private static final Map<Object, Object> ATTRIBUTES = new ConcurrentHashMap<>();

    private ModelScope() {}

    /** {@code registry} is a registry key identifier. */
    public static boolean skipsRegistry(Object registry) {
        return SKIPPED_REGISTRIES.contains(registry.toString());
    }

    /** Stands in for {@code owner.method().build()} in the default attribute table. */
    public static Object attributes(String owner, String method) {
        return new Deferred(owner, method);
    }

    /** The default attribute table's value for {@code key}, built once per entity type like vanilla. */
    public static Object attributes(Map<?, ?> table, Object key) throws ReflectiveOperationException {
        Object value = table.get(key);
        if (!(value instanceof Deferred deferred)) return value;
        Object built = ATTRIBUTES.get(key);
        if (built != null) return built;
        Class<?> owner = Class.forName(deferred.owner.replace('/', '.'), true, ModelScope.class.getClassLoader());
        Method factory = owner.getMethod(deferred.method);
        Object builder = factory.invoke(null);
        built = builder.getClass().getMethod("build").invoke(builder);
        Object raced = ATTRIBUTES.putIfAbsent(key, built);
        return raced == null ? built : raced;
    }

    private record Deferred(String owner, String method) {}
}
