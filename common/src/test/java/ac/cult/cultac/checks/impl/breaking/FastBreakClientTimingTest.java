package ac.cult.cultac.checks.impl.breaking;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.grim.grimac.api.storage.verbose.VerboseSchema;
import ac.grim.grimac.api.storage.verbose.VerboseTags;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.anticheat.update.BlockBreak;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drives FastBreak with the packet timelines a vanilla 26.2 client produces
 * (MultiPlayerGameMode#startDestroyBlock / #continueDestroyBlock, called from
 * Minecraft#startAttack and #continueAttack once per client tick).
 *
 * <p>A click runs startAttack and then continueAttack in the same tick, so the
 * START tick already performs a destroyDelay decrement or a progress step, and
 * startDestroyBlock never waits for destroyDelay. The 5-tick destroyDelay set
 * by a STOP is only consumed by continueDestroyBlock, before the next progress
 * step of the next block.
 */
public final class FastBreakClientTimingTest {
    private static final Identifier HASTE_MODIFIER =
            Identifier.fromNamespaceAndPath("crabbyenchants", "haste-block-break-speed");
    private static final Identifier EFFICIENCY_MODIFIER =
            Identifier.fromNamespaceAndPath("minecraft", "enchantment.efficiency/mainhand");

    private BlockState STONE;
    private CultPlayer player;
    private FastBreak check;
    private long now;
    private double peakBalance;
    private int x;

    @Before
    public void setup() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        // FastBreak's verbose uses {block}; register it with the VerboseCodecs wire shape
        // (as VerboseTemplateAuditTest does) without pulling in every production codec.
        VerboseTags.register("block", List.of(VerboseSchema.TypeTag.ZZ),
                (in, ctx, out, fmt) -> in.skip(VerboseSchema.TypeTag.ZZ.tag()));
        STONE = Blocks.STONE.defaultBlockState();
        player = new CultPlayer(new User(new User.Profile(UUID.randomUUID(), "FastBreak_Test"),
                null, null, null, new EmbeddedChannel()));
        Field version = CultPlayer.class.getDeclaredField("resolvedClientVersion");
        version.setAccessible(true);
        version.set(player, ClientVersion.V_26_2);
        // Offline players have no datastore to record flags into. A disabled player
        // still runs the check's accounting; the flag condition is balance > 1000.
        player.setDisabled(true);
        player.gamemode = GameMode.SURVIVAL;
        player.packetStateData.packetPlayerOnGround = true;
        player.y = 64;
        check = new FastBreak(player);
        check.clock = () -> now;
        now = 1_000_000L;
    }

    @After
    public void close() {
        Object channel = player.user.getChannel();
        player.onRemove();
        if (channel instanceof EmbeddedChannel embeddedChannel) {
            embeddedChannel.runPendingTasks();
            embeddedChannel.runScheduledPendingTasks();
            embeddedChannel.close();
        }
    }

    @Test
    public void hasteModifierIsAppliedExactlyAsTheEnchantSendsIt() {
        holdHastePickaxe(4, 0.6);
        // CrabbyEnchants: ADD_SCALAR (ADD_MULTIPLIED_BASE) 0.6 on base 1.0.
        assertEquals((double) 1.6F, player.compensatedEntities.getSelf().blockBreakSpeed, 0);
        assertEquals(17, player.compensatedEntities.getSelf().miningEfficiency, 0);
        // Netherite 9 + Efficiency IV 17, times Haste III 1.6: two progress ticks on stone.
        assertEquals((26F * 1.6F) / 1.5F / 30F, check.clientBlockDamage(STONE), 1e-7);
    }

    @Test
    public void heldMiningWithHasteNeverAccumulates() {
        holdHastePickaxe(4, 0.6);
        // continueDestroyBlock path: STOP, five delay ticks, START on tick 6,
        // progress on ticks 7 and 8, STOP on tick 8.
        int tick = 0;
        for (int block = 0; block < 40; block++) {
            start(tick);
            stop(tick + 2);
            tick += 8;
        }
        assertNoAdvantage();
    }

    @Test
    public void tapMiningWithHasteAfterEachBreakIsLegit() {
        holdHastePickaxe(4, 0.6);
        // Release after the STOP and click three ticks later (delay=150ms in the
        // live verbose). startAttack sends START immediately; the START tick and
        // the next four ticks consume destroyDelay, then two progress ticks.
        int tick = 0;
        for (int block = 0; block < 40; block++) {
            start(tick);
            stop(tick + 6);
            tick += 9;
        }
        assertNoAdvantage();
    }

    @Test
    public void clickAfterDestroyDelayExpiredProgressesOnTheStartTick() {
        holdHastePickaxe(4, 0.6);
        // Hold through the five delay ticks, release, click: the START tick
        // already adds progress, so a two-tick block ends one tick after START
        // (diff=50ms in the old model).
        int tick = 0;
        for (int block = 0; block < 40; block++) {
            start(tick);
            stop(tick + 1);
            tick += 8;
        }
        assertNoAdvantage();
    }

    @Test
    public void clickedInstantBreaksAfterAFinishedBlockAreLegit() {
        // Efficiency V + Haste III instamines stone; a slower block still sends STOP.
        holdHastePickaxe(5, 0.6);
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        assertTrue(check.clientBlockDamage(STONE) >= 1);
        assertTrue(check.clientBlockDamage(deepslate) < 1);
        int tick = 0;
        for (int vein = 0; vein < 20; vein++) {
            start(tick, deepslate);
            stop(tick + 2, deepslate);
            // startAttack instabreaks without consulting destroyDelay.
            start(tick + 3);
            start(tick + 5);
            start(tick + 7);
            tick += 10;
        }
        assertNoAdvantage();
    }

    @Test
    public void finishingBeforeTheRequiredProgressTicksStillFlags() {
        // Plain diamond pickaxe without Haste: stone needs six progress ticks.
        holdPickaxe(Material.DIAMOND_PICKAXE, 0, 1.0);
        assertEquals(6, Math.ceil(1 / check.clientBlockDamage(STONE)), 0);
        int tick = 0;
        for (int block = 0; block < 20; block++) {
            start(tick);
            stop(tick + 3);
            tick += 20;
        }
        assertFlagged();
    }

    @Test
    public void skippingDestroyDelayStillFlags() {
        holdHastePickaxe(4, 0.6);
        // START on the tick after STOP and finish after the full two progress
        // ticks: vanilla needs five destroyDelay ticks between the two blocks.
        int tick = 0;
        for (int block = 0; block < 40; block++) {
            start(tick);
            stop(tick + 2);
            tick += 3;
        }
        assertFlagged();
    }

    private void assertFlagged() {
        // Balances are clamped to the 1000ms threshold after accounting; once at the
        // cap every further booked advantage flags and cancels.
        assertTrue("peak=" + peakBalance, peakBalance >= 1000);
    }

    private void assertNoAdvantage() {
        // Exact vanilla timing must never be booked as an advantage at all.
        assertTrue("peak=" + peakBalance, peakBalance <= 0);
    }

    private double balance() {
        double max = Double.NEGATIVE_INFINITY;
        for (Field field : FastBreak.class.getDeclaredFields()) {
            if (field.getType() == double.class && field.getName().endsWith("Balance")) {
                try {
                    field.setAccessible(true);
                    max = Math.max(max, field.getDouble(check));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return max;
    }

    private void holdHastePickaxe(int efficiency, double haste) {
        holdPickaxe(Material.NETHERITE_PICKAXE, efficiency, haste);
    }

    private void holdPickaxe(Material type, int efficiency, double haste) {
        ItemStack pickaxe = new ItemStack(type);
        if (efficiency > 0) {
            pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, efficiency);
        }
        player.getInventory().inventory.setHeldItem(pickaxe);
        player.compensatedEntities.updateAttributes(player.entityID, List.of(
                new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.BLOCK_BREAK_SPEED, 1.0D,
                        haste == 1.0 ? List.of() : List.of(new AttributeModifier(HASTE_MODIFIER, haste,
                                AttributeModifier.Operation.ADD_MULTIPLIED_BASE))),
                new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.MINING_EFFICIENCY, 0.0D,
                        efficiency == 0 ? List.of() : List.of(new AttributeModifier(EFFICIENCY_MODIFIER,
                                efficiency * efficiency + 1, AttributeModifier.Operation.ADD_VALUE)))));
    }

    private void start(int tick) {
        start(tick, STONE);
    }

    private void start(int tick, BlockState state) {
        action(tick, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, state);
        x++;
    }

    private void stop(int tick) {
        stop(tick, STONE);
    }

    private void stop(int tick, BlockState state) {
        x--;
        action(tick, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, state);
        x++;
    }

    private void action(int tick, ServerboundPlayerActionPacket.Action action, BlockState state) {
        now = 1_000_000L + tick * 50L;
        check.onBlockBreak(new BlockBreak(player, new BlockPos(x, 64, 0), BlockFace.NORTH,
                Direction.NORTH.get3DDataValue(), action, 0, state));
        peakBalance = Math.max(peakBalance, balance());
    }
}
