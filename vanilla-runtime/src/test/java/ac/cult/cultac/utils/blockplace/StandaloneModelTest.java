package ac.cult.cultac.utils.blockplace;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.vanilla.VanillaBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class StandaloneModelTest {
    private static VanillaBootstrap model;

    @org.junit.jupiter.api.BeforeAll
    static void startModel() {
        model = VanillaBootstrap.open();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopModel() {
        if (model != null) model.close();
    }

    @Test
    void vanillaModelPlacesBlocksWithoutBukkitOrServer() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.Bukkit"));
        try (var runtime = ac.cult.placement.PlacementRuntime.openVanilla(
                java.nio.file.Path.of(System.getProperty("placementRuntimeJar")))) {
            model.newConnection().execute(() -> {
                assertTrue(model.registries().registries().count() > 20);
                var inventory = new java.util.ArrayList<>(
                        java.util.Collections.nCopies(43, ac.cult.placement.api.InteractionEngine.Stack.EMPTY));
                inventory.set(0, ac.cult.placement.api.InteractionEngine.Stack.vanilla("minecraft:dirt", 3));
                var actor = new ac.cult.placement.api.InteractionEngine.Actor(
                        .5, 64, -2, 0, 30, "STANDING", "SURVIVAL", false, 20, false, 0, inventory, false, 1, 4.5);
                var world = new ac.cult.placement.api.PlacementEngine.World() {
                    public int stateAt(int x, int y, int z) {
                        return net.minecraft.world.level.block.Block.getId(
                                (y <= 63 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                    }

                    public int minY() {
                        return -64;
                    }

                    public int height() {
                        return 384;
                    }

                    public boolean loaded(int x, int z) {
                        return true;
                    }
                };
                var client = new ac.cult.cultac.utils.latency.ClientComponentRegistries();
                var context =
                        new ac.cult.cultac.utils.minecraft.MinecraftRegistries(model::registries, model::resources);
                var result = runtime.interact(new ac.cult.placement.api.InteractionEngine.Request(
                        ac.cult.placement.api.InteractionEngine.Operation.USE_ON,
                        world,
                        actor,
                        "MAIN_HAND",
                        new ac.cult.placement.api.PlacementEngine.Pos(0, 63, 0),
                        "UP",
                        .5,
                        64,
                        .5,
                        false,
                        "minecraft:overworld",
                        null));
                assertTrue(result.consumes());
                assertEquals(
                        java.util.List.of(new ac.cult.placement.api.PlacementEngine.Write(
                                new ac.cult.placement.api.PlacementEngine.Pos(0, 64, 0),
                                net.minecraft.world.level.block.Block.getId(Blocks.DIRT.defaultBlockState()))),
                        result.writes());
                assertEquals(2, result.inventory().get(0).count());
                assertEquals(3, inventory.get(0).count());
                var supportTag = net.minecraft.resources.Identifier.parse("minecraft:supports_vegetation");
                var customTag = net.minecraft.resources.Identifier.parse("cult:test");
                int stone = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(Blocks.STONE);
                client.appendTags(new ac.cult.cultac.network.packet.RegistryTags(java.util.Map.of(
                        net.minecraft.core.registries.Registries.BLOCK,
                        new net.minecraft.tags.TagNetworkSerialization.NetworkPayload(java.util.Map.of(
                                supportTag,
                                it.unimi.dsi.fastutil.ints.IntList.of(stone),
                                customTag,
                                it.unimi.dsi.fastutil.ints.IntList.of(stone))))));
                var receivedTags = client.geometryTags(context);
                assertEquals(
                        java.util.List.of("minecraft:stone"),
                        receivedTags.blocks().get("cult:test"));
                assertSame(receivedTags, client.geometryTags(context));
                var taggedWorld = new ac.cult.placement.api.PlacementEngine.World() {
                    public int stateAt(int x, int y, int z) {
                        return world.stateAt(x, y, z);
                    }

                    public int minY() {
                        return world.minY();
                    }

                    public int height() {
                        return world.height();
                    }

                    public boolean loaded(int x, int z) {
                        return true;
                    }

                    public ac.cult.placement.api.GeometryTags tags() {
                        return client.geometryTags(context);
                    }
                };
                var flower = new ac.cult.placement.api.PlacementEngine.Request(
                        taggedWorld,
                        "minecraft:dandelion",
                        2,
                        java.util.Map.of(),
                        new ac.cult.placement.api.PlacementEngine.Pos(0, 63, 0),
                        "UP",
                        .5,
                        64,
                        .5,
                        false,
                        .5,
                        64,
                        -2,
                        0,
                        30,
                        false,
                        false);
                assertTrue(runtime.place(flower).consumes(), "Received numeric tag IDs must control native placement");
                client.appendTags(new ac.cult.cultac.network.packet.RegistryTags(java.util.Map.of(
                        net.minecraft.core.registries.Registries.BLOCK,
                        net.minecraft.tags.TagNetworkSerialization.NetworkPayload.EMPTY)));
                assertFalse(runtime.place(flower).consumes(), "An empty received payload must clear custom support");

                // A shovel's default block transformer (a vanilla data registry entry) makes a path.
                var shovelInventory = new java.util.ArrayList<>(
                        java.util.Collections.nCopies(43, ac.cult.placement.api.InteractionEngine.Stack.EMPTY));
                shovelInventory.set(
                        0, ac.cult.placement.api.InteractionEngine.Stack.vanilla("minecraft:iron_shovel", 1));
                var shovelActor = new ac.cult.placement.api.InteractionEngine.Actor(
                        .5, 64, -2, 0, 30, "STANDING", "SURVIVAL", false, 20, false, 0, shovelInventory, false, 1, 4.5);
                var grass = new ac.cult.placement.api.PlacementEngine.World() {
                    public int stateAt(int x, int y, int z) {
                        return net.minecraft.world.level.block.Block.getId(
                                (x == 0 && y == 64 && z == 0 ? Blocks.GRASS_BLOCK : y == 63 ? Blocks.STONE : Blocks.AIR)
                                        .defaultBlockState());
                    }

                    public int minY() {
                        return -64;
                    }

                    public int height() {
                        return 384;
                    }

                    public boolean loaded(int x, int z) {
                        return true;
                    }
                };
                var path = runtime.interact(new ac.cult.placement.api.InteractionEngine.Request(
                        ac.cult.placement.api.InteractionEngine.Operation.USE_ON,
                        grass,
                        shovelActor,
                        "MAIN_HAND",
                        new ac.cult.placement.api.PlacementEngine.Pos(0, 64, 0),
                        "UP",
                        .5,
                        65,
                        .5,
                        false,
                        "minecraft:overworld",
                        null));
                assertTrue(path.consumes());
                assertEquals(
                        java.util.List.of(new ac.cult.placement.api.PlacementEngine.Write(
                                new ac.cult.placement.api.PlacementEngine.Pos(0, 64, 0),
                                net.minecraft.world.level.block.Block.getId(Blocks.DIRT_PATH.defaultBlockState()))),
                        path.writes());
            });
        }
    }

    @Test
    void playTagUpdatesWaitForTheirBoundaryAndCanClearTags() {
        var state = model.newConnection();
        state.execute(() -> {
            var tag = net.minecraft.tags.BlockTags.CLIMBABLE;
            Runnable apply = state.preparePlayTags(java.util.Map.of(
                    net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.tags.TagNetworkSerialization.NetworkPayload.EMPTY));
            assertTrue(Blocks.LADDER.defaultBlockState().is(tag));
            apply.run();
            assertFalse(Blocks.LADDER.defaultBlockState().is(tag));
        });
        model.newConnection()
                .execute(
                        () -> assertTrue(Blocks.LADDER.defaultBlockState().is(net.minecraft.tags.BlockTags.CLIMBABLE)));
    }

    @Test
    void equivalentSessionsDoNotRebuildNativeHolderTags() throws Exception {
        var first = model.newConnection();
        var second = model.newConnection();
        var holder = net.minecraft.core.registries.BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.LADDER);
        var tagsField = net.minecraft.core.Holder.Reference.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        first.execute(first::finish);
        Object initialTags = tagsField.get(holder);
        second.execute(second::finish);
        assertSame(initialTags, tagsField.get(holder), "Identical configuration must reuse installed tag bindings");
        for (int i = 0; i < 100; i++) {
            first.execute(
                    () -> assertTrue(Blocks.LADDER.defaultBlockState().is(net.minecraft.tags.BlockTags.CLIMBABLE)));
            second.execute(
                    () -> assertTrue(Blocks.LADDER.defaultBlockState().is(net.minecraft.tags.BlockTags.CLIMBABLE)));
        }
        assertSame(initialTags, tagsField.get(holder), "Switching players must not rebuild identical holder tag sets");
    }

    @Test
    void connectionTagsAreIsolatedAcrossThreadsAndNestedCalls() throws Exception {
        var first = model.newConnection();
        var second = model.newConnection();
        var key = net.minecraft.core.registries.Registries.BLOCK;
        var tag = net.minecraft.tags.BlockTags.CLIMBABLE;
        first.execute(() -> {
            first.appendTags(java.util.Map.of(
                    key,
                    new net.minecraft.tags.TagNetworkSerialization.NetworkPayload(java.util.Map.of(
                            tag.location(),
                            it.unimi.dsi.fastutil.ints.IntList.of(
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(Blocks.STONE))))));
            first.finish();
        });
        second.execute(() -> {
            second.appendTags(java.util.Map.of(
                    key,
                    new net.minecraft.tags.TagNetworkSerialization.NetworkPayload(java.util.Map.of(
                            tag.location(),
                            it.unimi.dsi.fastutil.ints.IntList.of(
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(Blocks.DIRT))))));
            second.finish();
        });
        first.execute(() -> {
            assertTrue(Blocks.STONE.defaultBlockState().is(tag));
            assertFalse(Blocks.DIRT.defaultBlockState().is(tag));
            assertThrows(
                    IllegalStateException.class,
                    () -> second.execute(() -> {
                        assertFalse(Blocks.STONE.defaultBlockState().is(tag));
                        assertTrue(Blocks.DIRT.defaultBlockState().is(tag));
                        throw new IllegalStateException("nested task failed");
                    }));
            assertTrue(Blocks.STONE.defaultBlockState().is(tag));
            assertFalse(Blocks.DIRT.defaultBlockState().is(tag));
        });
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> {
                for (int i = 0; i < 100; i++)
                    first.execute(() -> {
                        assertTrue(Blocks.STONE.defaultBlockState().is(tag));
                        assertFalse(Blocks.DIRT.defaultBlockState().is(tag));
                    });
            });
            var b = workers.submit(() -> {
                for (int i = 0; i < 100; i++)
                    second.execute(() -> {
                        assertTrue(Blocks.DIRT.defaultBlockState().is(tag));
                        assertFalse(Blocks.STONE.defaultBlockState().is(tag));
                    });
            });
            a.get();
            b.get();
        }
        // New sessions start with vanilla bindings, even after another backend changed tags.
        model.newConnection().execute(() -> {
            assertFalse(Blocks.STONE.defaultBlockState().is(tag));
            assertFalse(Blocks.DIRT.defaultBlockState().is(tag));
            assertTrue(Blocks.LADDER.defaultBlockState().is(tag));
        });
        assertThrows(IllegalStateException.class, first::finish);
    }
}
