package ac.cult.cultac.network;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.network.packet.RegistryTags;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.value.Direction;
import ac.cult.cultac.protocol.value.GameMode;
import ac.cult.cultac.utils.anticheat.update.BlockPlace;
import ac.cult.cultac.utils.blockplace.VanillaBlockActions;
import ac.cult.cultac.utils.latency.ClientComponentRegistries;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ReceivedPlacementTagsTest {
    @Test
    void nativePlacementUsesEachPlayersReceivedTagsAndReplacesOldMembership() throws Exception {
        // ClientPacketListener#handleUpdateTags applies the received bindings before
        // VegetationBlock#mayPlaceOn reads SUPPORTS_VEGETATION during item use.
        try (var vanilla = new VanillaActionFixture();
                var services = new RecordConsumerServices();
                var fixture = new RecordReceiveFixture(ProtocolVersion.V26_3)) {
            var player = fixture.player;
            player.gamemode = GameMode.SURVIVAL;
            player.x = .5;
            player.y = 64;
            player.z = -2;
            var support = new BlockPos(1, 63, 1);
            var placed = support.above();
            var world = player.compensatedWorld;
            world.ensureValidationChunkLoaded(0, 0);
            world.updateBlock(support, Blocks.STONE.defaultBlockState());
            assertFalse(Blocks.STONE.defaultBlockState().is(BlockTags.SUPPORTS_VEGETATION));
            player.registryState = new ClientComponentRegistries();
            player.registryState.appendTags(new RegistryTags(Map.of(
                    Registries.BLOCK,
                    new TagNetworkSerialization.NetworkPayload(Map.of(
                            Identifier.parse("minecraft:supports_vegetation"),
                            it.unimi.dsi.fastutil.ints.IntList.of(BuiltInRegistries.BLOCK.getId(Blocks.STONE)))))));

            place(fixture, support);
            assertEquals(Blocks.SUNFLOWER, world.getBlockStateAt(placed).getBlock());
            assertEquals(Blocks.SUNFLOWER, world.getBlockStateAt(placed.above()).getBlock());
            assertTrue(player.getInventory().getHeldItem().isEmpty());
            assertFalse(
                    Blocks.STONE.defaultBlockState().is(BlockTags.SUPPORTS_VEGETATION),
                    "Client tag updates must not mutate host registry bindings");

            world.updateBlock(placed, Blocks.AIR.defaultBlockState());
            world.updateBlock(placed.above(), Blocks.AIR.defaultBlockState());
            player.registryState.appendTags(
                    new RegistryTags(Map.of(Registries.BLOCK, TagNetworkSerialization.NetworkPayload.EMPTY)));
            place(fixture, support);
            assertTrue(world.getBlockStateAt(placed).isAir());
            assertTrue(world.getBlockStateAt(placed.above()).isAir());
            assertEquals(1, player.getInventory().getHeldItem().getCount());

            player.registryState = null;
            place(fixture, support);
            assertTrue(world.getBlockStateAt(placed).isAir(), "Received tags must not leak to the default client view");
            assertEquals(1, player.getInventory().getHeldItem().getCount());
        }
    }

    private static void place(RecordReceiveFixture fixture, BlockPos support) {
        var player = fixture.player;
        var stack = new ItemStack(Items.SUNFLOWER);
        player.getInventory().inventory.setHeldItem(stack);
        var place = new BlockPlace(player, InteractionHand.MAIN_HAND, support, Direction.UP, stack, null);
        place.setCursor(new Vec3(.5, 1, .5));
        VanillaBlockActions.useOn(player, place);
    }
}
