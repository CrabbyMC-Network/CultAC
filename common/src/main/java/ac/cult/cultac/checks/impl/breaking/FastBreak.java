package ac.cult.cultac.checks.impl.breaking;

import net.minecraft.network.protocol.Packet;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.type.BlockBreakListener;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.network.CultPacketGroup;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.PacketGroup;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.anticheat.update.BlockBreak;
import ac.cult.cultac.utils.collisions.ViaClientBlockShapeMappings;
import ac.cult.cultac.utils.math.CultMath;
import ac.cult.cultac.utils.nmsutil.BlockBreakSpeed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.LongSupplier;

// Based loosely off of Hawk BlockBreakSpeedSurvival
// Also based loosely off of NoCheatPlus FastBreak
// Also based off minecraft wiki: https://minecraft.wiki/w/Breaking#Instant_breaking
@CheckData(name = "FastBreak", stableKey = "cult.breaking.fast_break", description = "Breaking blocks too quickly")
public class FastBreak extends Check implements BlockBreakListener {
    // The delay shape is no longer written; it stays so the stored verbose schema is unchanged.
    private static final Verbose V =
            Verbose.of("[delay={ulong}ms|diff={f64:%.1f}ms, balance={f64:%.1f}ms], type={block}");

    // For some reason these states flag and I don't know why.
    // Better to just exempt to not annoy legit players.
    private static final Set<Block> EXEMPT_STATES = Set.of();

    // Receive-time clock; replaceable so tests can replay exact client tick timelines.
    LongSupplier clock = System::currentTimeMillis;

    public FastBreak(CultPlayer player) {
        super(player);
    }

    // MultiPlayerGameMode#continueDestroyBlock sets destroyDelay = 5 after a finished break
    private static final int DESTROY_DELAY_TICKS = 5;
    private static final double TICK_MILLIS = 50;

    // The block the player is currently breaking
    BlockPos targetBlockPosition = null;
    // The maximum amount of damage the player deals to the block
    //
    double maximumBlockDamage = 0;
    // The last time a finish digging packet was sent; destroyDelay starts here
    long lastFinishBreak = 0;
    // The time the player started to break the block, to know how long the player waited until they finished breaking the block
    long startBreak = 0;

    // The buffer to this check
    double blockBreakBalance = 0;

    // Vanilla client timing (MultiPlayerGameMode, Minecraft#startAttack/#continueAttack):
    // - startDestroyBlock never consults destroyDelay. A click sends START (and instabreaks)
    //   immediately after a finished break, so no START-to-previous-STOP delay is enforced.
    // - continueDestroyBlock runs at most once per tick and either consumes one destroyDelay
    //   tick or adds one tick of progress. A click runs it in the START tick itself.
    // So a block needing n progress ticks finishes no earlier than n - 1 ticks after START,
    // and no earlier than 5 + n ticks after the previous finished break.
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) { // PE DiggingAction.START_DIGGING
            startBreak = clock.getAsLong();
            targetBlockPosition = blockBreak.position;

            // FIXME: getBlockDamage might not return the correct value if the player switched slots before this
            maximumBlockDamage = clientBlockDamage(blockBreak.block);
        }

        if (blockBreak.action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK && targetBlockPosition != null) { // PE DiggingAction.FINISHED_DIGGING
            long now = clock.getAsLong();
            // Every STOP is sent by a progress tick, so at least one is required
            double progressTicks = Math.max(1, Math.ceil(1 / maximumBlockDamage));
            double earliestFinish = Math.max(
                    startBreak + (progressTicks - 1) * TICK_MILLIS,
                    lastFinishBreak + (DESTROY_DELAY_TICKS + progressTicks) * TICK_MILLIS);
            double diff = earliestFinish - now;

            clampBalance();

            if (diff < 25) {  // Reduce buffer if "close enough"
                blockBreakBalance *= 0.9;
            } else { // Otherwise, increase buffer
                blockBreakBalance += diff;
            }

            if (blockBreakBalance > 1000) { // If more than a second of advantage
                int type = BuiltInRegistries.BLOCK.getId(blockBreak.block.getBlock());
                if (flag(V.write(verbose()).bool(false).ulong(0).f64(diff).f64(blockBreakBalance).sint(type)) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }

            // also set start time because the breaking netcode is fucked on 1.14.4+
            lastFinishBreak = startBreak = now;
        }
    }

    // Find the most optimal block damage using the animation packet, which is sent at least once a tick when breaking blocks
    // On 1.8 clients, via screws with this packet meaning we must fall back to the 1.8 idle flying packet
    //
    // listen for flying packets because some block breaks can happen before the next animation (somehow???), causing onGround desync
    @CultPacketHandler
    @CultPacketGroup(PacketGroup.SERVERBOUND_PLAYER_MOVEMENT)
    public void onMovePlayer(PacketReceiveEvent event, CultPlayer player, ServerboundMovePlayerPacket packet) {
        updateMaximumBlockDamage();
    }


    @CultPacketHandler(packetClass = "net.minecraft.network.protocol.game.ServerboundPunchPacket")
    public void onPunch(PacketReceiveEvent event, CultPlayer player, Packet<?> packet) {
        onSwing(event, player, packet);
    }

    @CultPacketHandler(packetClass = "net.minecraft.network.protocol.game.ServerboundSwingPacket")
    public void onSwing(PacketReceiveEvent event, CultPlayer player, Packet<?> packet) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            updateMaximumBlockDamage();
        }
    }

    private void updateMaximumBlockDamage() {
        if (targetBlockPosition != null) {
            maximumBlockDamage = Math.max(maximumBlockDamage,
                    BlockBreakSpeed.getBlockDamage(player, targetBlockPosition));
        }
    }

    double clientBlockDamage(BlockState serverState) {
        BlockState clientState = ViaClientBlockShapeMappings.clientBlockState(player, serverState);
        return BlockBreakSpeed.getBlockDamage(player, player.getInventory().getHeldItem(), clientState);
    }

    private void clampBalance() {
        double balance = Math.max(1000, (player.getTransactionPing()));
        blockBreakBalance = CultMath.clamp(blockBreakBalance, -balance, balance); // Clamp not Math.max in case other logic changes
    }
}
