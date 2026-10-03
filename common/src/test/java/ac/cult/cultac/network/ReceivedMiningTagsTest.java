package ac.cult.cultac.network;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.network.packet.RegistryTags;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.utils.latency.ClientComponentRegistries;
import ac.cult.cultac.utils.nmsutil.BlockBreakSpeed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class ReceivedMiningTagsTest {
    @Test
    void miningUsesReceivedTagMembershipWithoutChangingServerBindings() throws Exception {
        try (var services = new RecordConsumerServices();
                var fixture = new RecordReceiveFixture(ProtocolVersion.V26_3)) {
            var player = fixture.player;
            player.packetStateData.packetPlayerOnGround = true;
            var shovel = new ItemStack(Items.IRON_SHOVEL);
            var stone = Blocks.STONE.defaultBlockState();
            assertEquals(1.0F / 1.5F / 100, BlockBreakSpeed.getBlockDamage(player, shovel, stone), 1e-8);
            player.registryState = new ClientComponentRegistries();
            player.registryState.appendTags(new RegistryTags(java.util.Map.of(
                    Registries.BLOCK,
                    new TagNetworkSerialization.NetworkPayload(java.util.Map.of(
                            Identifier.parse("minecraft:mineable/shovel"),
                            it.unimi.dsi.fastutil.ints.IntList.of(BuiltInRegistries.BLOCK.getId(Blocks.STONE)))))));
            assertEquals(6.0F / 1.5F / 30, BlockBreakSpeed.getBlockDamage(player, shovel, stone), 1e-8);
            assertFalse(stone.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL));
            player.registryState.appendTags(
                    new RegistryTags(java.util.Map.of(Registries.BLOCK, TagNetworkSerialization.NetworkPayload.EMPTY)));
            assertEquals(1.0F / 1.5F / 100, BlockBreakSpeed.getBlockDamage(player, shovel, stone), 1e-8);
        }
    }
}
