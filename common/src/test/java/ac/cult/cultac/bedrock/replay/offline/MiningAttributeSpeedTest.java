package ac.cult.cultac.bedrock.replay.offline;

import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.nmsutil.BlockBreakSpeed;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MiningAttributeSpeedTest {
    private CultPlayer player;

    @Before public void setup() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        player = new CultPlayer(new User(new User.Profile(UUID.randomUUID(), "Mining_Test"),
                null, null, null, new EmbeddedChannel()));
        version(ClientVersion.V_26_2);
        player.gamemode = GameMode.SURVIVAL;
        player.packetStateData.packetPlayerOnGround = true;
        player.y = 64;
    }

    @After public void close() { OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player); }

    @Test public void hasteAttributeWorksWithoutEfficiencyAtEveryCeLevel() {
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        double base = damage(pick);
        for (double multiplier : new double[]{1.2, 1.4, 1.6}) {
            attributes(multiplier, 0);
            assertEquals(base * (float)multiplier, damage(pick), 1e-7);
        }
    }

    @Test public void modernEfficiencyUsesCompleteAttributeWithoutAddingEnchantTwice() {
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        attributes(1, 26);
        // Diamond speed 8 plus the attribute's Efficiency V contribution 26.
        assertEquals(34f / 1.5f / 30f, damage(pick), 1e-7);
        attributes(1.6, 30);
        assertEquals((38f * 1.6f) / 1.5f / 30f, damage(pick), 1e-7);
    }

    @Test public void miningEfficiencyDoesNotSpeedUpIneffectiveToolsButBreakSpeedDoes() {
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        double base = damage(axe);
        attributes(1, 100);
        assertEquals(base, damage(axe), 0);
        attributes(1.6, 100);
        assertEquals(1.6f / 1.5f / 100f, damage(axe), 1e-7);
    }

    @Test public void zeroBreakSpeedPreventsMiningRatherThanGrantingLenience() {
        attributes(0, 26);
        assertEquals(0, damage(new ItemStack(Material.DIAMOND_PICKAXE)), 0);
    }

    @Test public void legacyClientIgnoresUnsupportedAttributesAndKeepsEnchantFormula() throws Exception {
        version(ClientVersion.V_1_20_3);
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        attributes(1.6, 100);
        assertEquals(34f / 1.5f / 30f, damage(pick), 1e-7);
    }

    @Test public void firstBreakSpeedClientStillUsesLegacyEfficiency() throws Exception {
        version(ClientVersion.V_1_20_5);
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        attributes(1.6, 100);
        assertEquals((34f * 1.6f) / 1.5f / 30f, damage(pick), 1e-7);
    }

    @Test public void airbornePenaltyStillAppliesAfterCustomBreakSpeed() {
        attributes(1.6, 0);
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        double grounded = damage(pick);
        player.packetStateData.packetPlayerOnGround = false;
        player.boundingBox = null;
        assertEquals(grounded / 5, damage(pick), 1e-7);
    }

    private double damage(ItemStack tool) {
        return BlockBreakSpeed.getBlockDamage(player, tool, Blocks.STONE.defaultBlockState());
    }

    private void attributes(double breakSpeed, double efficiency) {
        player.compensatedEntities.updateAttributes(player.entityID, List.of(
                new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.BLOCK_BREAK_SPEED, breakSpeed, List.of()),
                new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.MINING_EFFICIENCY, efficiency, List.of())));
    }

    private void version(ClientVersion version) throws Exception {
        Field field = CultPlayer.class.getDeclaredField("resolvedClientVersion");
        field.setAccessible(true);
        field.set(player, version);
    }
}
