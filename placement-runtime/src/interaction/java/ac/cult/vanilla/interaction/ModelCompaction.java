package ac.cult.vanilla.interaction;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One startup pass that shares equal immutable block data across states, after bootstrap
 * has built it and before the model serves requests. Nothing is mutated afterwards.
 *
 * <ul>
 *   <li>Shapes are keyed by exact value: shape class, voxel occupancy and the raw bits of
 *       every coordinate. Equal shapes, coordinate lists, voxel grids and face arrays
 *       become one instance. Shapes held inside vanilla's immutable per-block maps keep their
 *       identity, but their internals are shared.</li>
 *   <li>Per-state caches equal in shape identity, flags and face sturdiness become one.</li>
 *   <li>Each state's neighbor rows (the states reached by changing one property) are shared
 *       with every state on the same row.</li>
 * </ul>
 * The fields written here that vanilla declares final are made non-final by the loader's
 * narrowing transform, so no final field is mutated reflectively.
 */
final class ModelCompaction {
    private record ShapeKey(Class<?> type, List<Long> occupancy, List<Long> x, List<Long> y, List<Long> z) {}

    private record CacheKey(VoxelShape collision, boolean large, boolean full, List<Boolean> sturdy) {}

    private final IdentityHashMap<VoxelShape, VoxelShape> visited = new IdentityHashMap<>();
    private final Map<ShapeKey, VoxelShape> shapes = new HashMap<>();
    private final Map<List<Object>, Object> coordinates = new HashMap<>(), grids = new HashMap<>();
    private final Map<List<Object>, VoxelShape[]> shapeArrays = new HashMap<>();
    private final Map<CacheKey, Object> caches = new HashMap<>();
    private final Map<List<Object>, Object[]> rows = new HashMap<>();
    private final Field gridField, facesField, occlusionField, occlusionByFaceField, cacheField, neighborsField;
    private final Field[] coordinateFields;
    private final Field cacheCollision, cacheLarge, cacheFull, cacheSturdy;

    private ModelCompaction() throws ReflectiveOperationException {
        gridField = writable(VoxelShape.class, "shape");
        facesField = writable(VoxelShape.class, "faces");
        coordinateFields = new Field[] {
            writable(ArrayVoxelShape.class, "xs"),
            writable(ArrayVoxelShape.class, "ys"),
            writable(ArrayVoxelShape.class, "zs")
        };
        occlusionField = writable(BlockBehaviour.BlockStateBase.class, "occlusionShape");
        occlusionByFaceField = writable(BlockBehaviour.BlockStateBase.class, "occlusionShapesByFace");
        cacheField = writable(BlockBehaviour.BlockStateBase.class, "cache");
        neighborsField = writable(StateHolder.class, "neighbors");
        Class<?> cache = cacheField.getType();
        cacheCollision = writable(cache, "collisionShape");
        cacheLarge = readable(cache, "largeCollisionShape");
        cacheFull = readable(cache, "isCollisionShapeFullBlock");
        cacheSturdy = readable(cache, "faceSturdy");
    }

    /** Compacts the narrowed model (whose loader made the written fields non-final). */
    static void run() {
        try {
            new ModelCompaction().compact();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Unable to compact the vanilla block model", failure);
        }
    }

    private static Field readable(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field writable(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = readable(owner, name);
        if (Modifier.isFinal(field.getModifiers()))
            throw new IllegalStateException("Compacted field is still final: " + owner.getName() + "." + name);
        return field;
    }

    private void compact() throws ReflectiveOperationException {
        var scanned = new HashSet<Class<?>>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (Class<?> type = block.getClass(); type != Object.class; type = type.getSuperclass()) {
                boolean statics = scanned.add(type);
                for (Field field : type.getDeclaredFields()) {
                    boolean isStatic = Modifier.isStatic(field.getModifiers());
                    if (isStatic && !statics || field.getType().isPrimitive()) continue;
                    if (!field.trySetAccessible()) continue;
                    visitValue(field.get(isStatic ? null : block), 0);
                }
            }
        }
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            occlusionField.set(state, shape((VoxelShape) occlusionField.get(state)));
            occlusionByFaceField.set(state, shapeArray((VoxelShape[]) occlusionByFaceField.get(state)));
            Object cache = cacheField.get(state);
            if (cache != null) cacheField.set(state, cache(cache));
            rows(state);
        }
        for (var fluid : BuiltInRegistries.FLUID)
            for (var state : fluid.getStateDefinition().getPossibleStates()) rows(state);
    }

    /** Shapes stored in block fields: directly, in arrays, maps, or captured by a per-state shape function. */
    private void visitValue(Object value, int depth) throws ReflectiveOperationException {
        if (value == null || depth > 2) return;
        if (value instanceof VoxelShape shape) shape(shape);
        else if (value instanceof VoxelShape[] array) {
            for (VoxelShape shape : array) shape(shape);
        } else if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) visitValue(element, depth + 1);
        } else if (value instanceof Object[] array
                && array.getClass().getComponentType().isAssignableFrom(VoxelShape.class)) {
            for (Object element : array) visitValue(element, depth + 1);
        } else if (value.getClass().isHidden() && value instanceof java.util.function.Function<?, ?>) {
            for (Field field : value.getClass().getDeclaredFields())
                if (!field.getType().isPrimitive() && field.trySetAccessible()) visitValue(field.get(value), depth + 1);
        }
    }

    private VoxelShape shape(VoxelShape shape) throws ReflectiveOperationException {
        if (shape == null) return null;
        VoxelShape known = visited.get(shape);
        if (known != null) return known;
        visited.put(shape, shape);
        if (shape instanceof ArrayVoxelShape)
            for (Field field : coordinateFields) {
                var list = (DoubleList) field.get(shape);
                field.set(shape, coordinates.computeIfAbsent(List.of(list.getClass(), bits(list)), key -> list));
            }
        // The face cache fills lazily: only complete arrays can be shared without one shape
        // later writing its own face into another's cache.
        var faces = (VoxelShape[]) facesField.get(shape);
        if (faces != null) {
            if (Arrays.asList(faces).contains(null)) {
                for (int i = 0; i < faces.length; i++) faces[i] = shape(faces[i]);
            } else facesField.set(shape, shapeArray(faces));
        }
        var grid = (DiscreteVoxelShape) gridField.get(shape);
        List<Long> occupancy = occupancy(grid);
        gridField.set(shape, grids.computeIfAbsent(List.of(grid.getClass(), occupancy), key -> grid));
        var key = new ShapeKey(
                shape.getClass(),
                occupancy,
                bits(shape.getCoords(Direction.Axis.X)),
                bits(shape.getCoords(Direction.Axis.Y)),
                bits(shape.getCoords(Direction.Axis.Z)));
        VoxelShape canonical = shapes.putIfAbsent(key, shape);
        if (canonical == null) return shape;
        visited.put(shape, canonical);
        return canonical;
    }

    private VoxelShape[] shapeArray(VoxelShape[] array) throws ReflectiveOperationException {
        if (array == null) return null;
        for (int i = 0; i < array.length; i++) array[i] = shape(array[i]);
        return shapeArrays.computeIfAbsent(Arrays.asList((Object[]) array), key -> array);
    }

    private Object cache(Object cache) throws ReflectiveOperationException {
        var collision = shape((VoxelShape) cacheCollision.get(cache));
        cacheCollision.set(cache, collision);
        boolean[] sturdy = (boolean[]) cacheSturdy.get(cache);
        var flags = new ArrayList<Boolean>(sturdy.length);
        for (boolean value : sturdy) flags.add(value);
        return caches.computeIfAbsent(
                new CacheKey(collision, cacheLarge.getBoolean(cache), cacheFull.getBoolean(cache), flags),
                key -> cache);
    }

    private void rows(StateHolder<?, ?> state) throws ReflectiveOperationException {
        var neighbors = (Object[][]) neighborsField.get(state);
        if (neighbors == null) return;
        for (int property = 0; property < neighbors.length; property++) {
            Object[] row = neighbors[property];
            neighbors[property] = rows.computeIfAbsent(Arrays.asList(row), key -> row);
        }
    }

    private static List<Long> bits(DoubleList values) {
        var bits = new ArrayList<Long>(values.size() + 1);
        for (int i = 0; i < values.size(); i++) bits.add(Double.doubleToRawLongBits(values.getDouble(i)));
        return bits;
    }

    private static List<Long> occupancy(DiscreteVoxelShape grid) {
        var key = new ArrayList<Long>();
        key.add((long) grid.getXSize());
        key.add((long) grid.getYSize());
        key.add((long) grid.getZSize());
        for (Direction.Axis axis : Direction.Axis.values()) {
            key.add((long) grid.firstFull(axis));
            key.add((long) grid.lastFull(axis));
        }
        long word = 0;
        int bit = 0;
        for (int x = 0; x < grid.getXSize(); x++)
            for (int y = 0; y < grid.getYSize(); y++)
                for (int z = 0; z < grid.getZSize(); z++) {
                    if (grid.isFull(x, y, z)) word |= 1L << bit;
                    if (++bit == 64) {
                        key.add(word);
                        word = 0;
                        bit = 0;
                    }
                }
        if (bit > 0) key.add(word);
        return key;
    }
}
