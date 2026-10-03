package ac.cult.cultac.vanilla;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Interners;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class VanillaComponentBindingsTest {
    private static final ResourceKey<Registry<String>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.parse("test:values"));
    private static final ResourceKey<String> ENTRY = ResourceKey.create(REGISTRY, Identifier.parse("test:entry"));
    private static final DataComponentType<Integer> VALUE = DataComponentType.<Integer>builder()
            .persistent(com.mojang.serialization.Codec.INT)
            .build();

    @Test
    void equivalentDefaultsShareBindingsAndChangedDefaultsRestore() {
        var holder = Holder.Reference.createStandAlone(new HolderOwner<String>() {}, ENTRY);
        var shared = Interners.<VanillaComponentBindings<?>>newWeakInterner();
        var first = shared.intern(binding(holder, 3));
        var equivalent = shared.intern(binding(holder, 3));
        var changed = shared.intern(binding(holder, 4));
        assertSame(first, equivalent);
        assertNotSame(first, changed);
        first.apply();
        assertEquals(3, holder.components().get(VALUE));
        changed.apply();
        assertEquals(4, holder.components().get(VALUE));
        equivalent.apply();
        assertEquals(3, holder.components().get(VALUE));
    }

    @Test
    void equalKeysAndValuesDoNotMergeHoldersFromDifferentRegistries() {
        var first = Holder.Reference.createStandAlone(new HolderOwner<String>() {}, ENTRY);
        var second = Holder.Reference.createStandAlone(new HolderOwner<String>() {}, ENTRY);
        var shared = Interners.<VanillaComponentBindings<?>>newWeakInterner();
        var a = shared.intern(binding(first, 3));
        var b = shared.intern(binding(second, 3));
        assertNotSame(a, b);
        a.apply();
        assertFalse(second.areComponentsBound());
        b.apply();
        binding(first, 7).apply();
        assertEquals(7, first.components().get(VALUE));
        assertEquals(3, second.components().get(VALUE));
    }

    @Test
    void contextTransitionsPreserveUnchangedMapsAndRestoreChangedAndEmptyDefaults() {
        var owner = new HolderOwner<String>() {};
        var stable = Holder.Reference.createStandAlone(owner, ENTRY);
        var mutable = Holder.Reference.createStandAlone(
                owner, ResourceKey.create(REGISTRY, Identifier.parse("test:mutable")));
        var stableMap = components(3);
        var original = binding(Map.of(stable, stableMap, mutable, components(7)));
        var changed = binding(Map.of(stable, components(3), mutable, components(8)));
        var cleared = binding(Map.of(stable, components(3), mutable, DataComponentMap.EMPTY));
        original.apply();
        for (int repeat = 0; repeat < 3; repeat++) {
            changed.applyAfter(original);
            assertSame(stableMap, stable.components());
            assertEquals(8, mutable.components().get(VALUE));
            cleared.applyAfter(changed);
            assertSame(stableMap, stable.components());
            assertNull(mutable.components().get(VALUE));
            original.applyAfter(cleared);
            assertSame(stableMap, stable.components());
            assertEquals(7, mutable.components().get(VALUE));
        }
    }

    @Test
    void transitionAcrossPrivateRegistriesStillRestoresSharedHolders() {
        var shared = Holder.Reference.createStandAlone(new HolderOwner<String>() {}, ENTRY);
        var privateHolder = Holder.Reference.createStandAlone(new HolderOwner<String>() {}, ENTRY);
        var first = binding(shared, 3);
        var changed = binding(shared, 7);
        var isolated = binding(privateHolder, 9);
        first.applyAfter(null);
        for (int repeat = 0; repeat < 3; repeat++) {
            changed.applyAfter(first);
            isolated.applyAfter(changed);
            first.applyAfter(isolated);
            assertEquals(3, shared.components().get(VALUE));
            assertEquals(9, privateHolder.components().get(VALUE));
        }
    }

    private static VanillaComponentBindings<String> binding(Holder.Reference<String> holder, int value) {
        return binding(Map.of(holder, components(value)));
    }

    private static DataComponentMap components(int value) {
        return DataComponentMap.builder().set(VALUE, value).build();
    }

    private static VanillaComponentBindings<String> binding(Map<Holder.Reference<String>, DataComponentMap> entries) {
        return new VanillaComponentBindings<>(new DataComponentInitializers.PendingComponents<>() {
            @Override
            public ResourceKey<? extends Registry<? extends String>> key() {
                return REGISTRY;
            }

            @Override
            public void forEach(BiConsumer<Holder.Reference<String>, DataComponentMap> output) {
                entries.forEach(output);
            }

            @Override
            public void apply() {
                entries.forEach(Holder.Reference::bindComponents);
            }
        });
    }
}
