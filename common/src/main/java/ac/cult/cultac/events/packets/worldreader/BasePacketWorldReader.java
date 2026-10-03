package ac.cult.cultac.events.packets.worldreader;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.network.packet.WorldPackets.BlockUpdate;
import ac.cult.cultac.network.packet.WorldPackets.Chunk;
import ac.cult.cultac.network.packet.WorldPackets.SectionBlocksUpdate;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundBlockChangedAck;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundBlockEvent;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundForgetLevelChunk;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundGameEvent;
import ac.cult.cultac.protocol.value.GameEventType;
import ac.cult.cultac.utils.data.TeleportData;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedChunk;
import ac.cult.cultac.utils.latency.CompensatedWorld.CachedSection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

public class BasePacketWorldReader {

    @CultPacketHandler
    public void onForgetLevelChunk(
            PacketSendEvent<ClientboundForgetLevelChunk> event, CultPlayer player, ClientboundForgetLevelChunk packet) {
        unloadChunk(event, player, packet.x(), packet.z());
    }

    @CultPacketHandler
    public void onLevelChunkWithLight(PacketSendEvent<Chunk> event, CultPlayer player, Chunk packet) {
        handleMapChunk(player, event, packet);
    }

    @CultPacketHandler
    public void onBlockUpdate(PacketSendEvent<BlockUpdate> event, CultPlayer player, BlockUpdate packet) {
        handleBlockChange(player, event, packet);
    }

    @CultPacketHandler
    public void onBlockEvent(
            PacketSendEvent<ClientboundBlockEvent> event, CultPlayer player, ClientboundBlockEvent packet) {
        handleBlockEvent(player, event, packet);
    }

    @CultPacketHandler
    public void onSectionBlocksUpdate(
            PacketSendEvent<SectionBlocksUpdate> event, CultPlayer player, SectionBlocksUpdate packet) {
        handleMultiBlockChange(player, event, packet);
    }

    @CultPacketHandler
    public void onBlockChangedAck(
            PacketSendEvent<ClientboundBlockChangedAck> event, CultPlayer player, ClientboundBlockChangedAck packet) {
        CultPlayer.TrackedTransaction transaction = player.createTrackedTransactionPacketForBundle();
        if (transaction != null) {

            event.getWritesAfterSend().add(transaction.packet());
            event.getTasksAfterSend().add(() -> player.markTrackedTransactionPacketSent(transaction));
            player.compensatedWorld.handlePredictionConfirmation(packet.sequence(), transaction);
        } else {
            player.compensatedWorld.handlePredictionConfirmation(packet.sequence());
        }
    }

    @CultPacketHandler
    public void onGameEvent(
            PacketSendEvent<ClientboundGameEvent> event, CultPlayer player, ClientboundGameEvent packet) {
        player.latencyUtils.addRealTimeTaskNow(() -> {
            if (packet.event() == GameEventType.START_RAINING) {
                player.compensatedWorld.isRaining = true;
            } else if (packet.event() == GameEventType.STOP_RAINING) {
                player.compensatedWorld.isRaining = false;
            } else if (packet.event() == GameEventType.RAIN_LEVEL_CHANGE) {
                player.compensatedWorld.isRaining = packet.param() > 0.2f;
            }
        });
    }

    public void handleMapChunk(CultPlayer player, PacketSendEvent<Chunk> event, Chunk packet) {
        // Subclasses decode the active chunk format.
    }

    public void addChunkToCache(
            PacketSendEvent<?> event,
            CultPlayer player,
            CachedSection[] chunks,
            boolean isGroundUp,
            int chunkX,
            int chunkZ) {
        addChunkToCache(event, player, chunks, isGroundUp, packetDimension(player), chunkX, chunkZ);
    }

    public void addChunkToCache(
            PacketSendEvent<?> event,
            CultPlayer player,
            CachedSection[] chunks,
            boolean isGroundUp,
            String dimension,
            int chunkX,
            int chunkZ) {
        addChunkToCache(event, player, chunks, isGroundUp, dimension, chunkX, chunkZ, List.of());
    }

    public void addChunkToCache(
            PacketSendEvent<?> event,
            CultPlayer player,
            CachedSection[] chunks,
            boolean isGroundUp,
            String dimension,
            int chunkX,
            int chunkZ,
            List<BlockPos> geyserTickers) {
        // The compensated world stores what this connection sees. Translate
        // before pooling so clients with different palettes cannot share wrong states.
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i] != null)
                chunks[i] = chunks[i].translated(state ->
                        ac.cult.cultac.utils.collisions.ViaClientBlockShapeMappings.clientBlockState(player, state));
        }
        double chunkCenterX = (chunkX << 4) + 8;
        double chunkCenterZ = (chunkZ << 4) + 8;
        boolean playerLoadingIntoChunk =
                Math.abs(player.x - chunkCenterX) < 16 && Math.abs(player.z - chunkCenterZ) < 16;

        for (TeleportData teleports : player.getSetbackTeleportUtil().pendingTeleports) {
            if (teleports.getFlags().getMask() != 0) {
                continue; // idk how to handle this... relative teleports SUCK for anticheats.
            }
            playerLoadingIntoChunk = playerLoadingIntoChunk
                    || (Math.abs(teleports.getLocation().x - chunkCenterX) < 16
                            && Math.abs(teleports.getLocation().z - chunkCenterZ) < 16);
        }

        if (playerLoadingIntoChunk) {
            // Wrap this between bread.
            player.sendTransaction();
        }
        int applyTransaction = appendTrailingProofTransaction(event, player);
        if (isGroundUp) {
            if (player.chunkDebug) {
                player.sendMessage("Chunk " + chunkX + " " + chunkZ + " was added.");
            }
            CachedChunk column = new CachedChunk(chunks, applyTransaction);
            player.compensatedWorld.addToCache(column, dimension, applyTransaction, chunkX, chunkZ, geyserTickers);
        } else {
            if (player.chunkDebug) {
                player.sendMessage("Chunk " + chunkX + " " + chunkZ + " was merged.");
            }
            player.latencyUtils.addRealTimeTask(applyTransaction, () -> {
                CachedChunk existingColumn = player.compensatedWorld.getChunk(chunkX, chunkZ);
                if (existingColumn == null) {
                    // Corrupting the player's empty chunk is actually quite meaningless
                    // You are able to set blocks inside it, and they do apply, it just always returns air despite what
                    // its data says
                    // So go ahead, corrupt the player's empty chunk and make it no longer all air, it doesn't matter
                    //
                    // LogUtil.warn("Invalid non-ground up continuous sent for empty chunk " + chunkX + " " + chunkZ + "
                    // for " + player.user.getProfile().getName() + "! This corrupts the player's empty chunk!");
                    return;
                }
                player.compensatedWorld.mergeIntoCache(
                        existingColumn, chunks, dimension, applyTransaction, chunkX, chunkZ);
            });
        }
    }

    private String packetDimension(CultPlayer player) {
        return player.compensatedWorld.getLastClientboundDimension().dimension();
    }

    public void unloadChunk(PacketSendEvent<?> event, CultPlayer player, int x, int z) {
        if (player == null) return;
        if (player.chunkDebug) {
            player.sendMessage("Chunk " + x + " " + z + " queued for unload.");
        }
        int applyTransaction = appendTrailingProofTransaction(event, player);
        player.compensatedWorld.removeChunkLater(packetDimension(player), x, z, applyTransaction);
    }

    public void unloadChunk(CultPlayer player, int x, int z) {
        if (player == null) return;
        if (player.chunkDebug) {
            player.sendMessage("Chunk " + x + " " + z + " queued for unload.");
        }
        player.compensatedWorld.removeChunkLater(x, z);
    }

    private int appendTrailingProofTransaction(PacketSendEvent<?> event, CultPlayer player) {
        CultPlayer.TrackedTransaction transaction = player.createTrackedTransactionPacketForDeferredSend();
        if (transaction == null) {
            return player.lastTransactionSent.get();
        }

        event.getWritesAfterSend().add(transaction.packet());
        event.getTasksAfterSend().add(() -> player.markTrackedTransactionPacketSent(transaction));
        return transaction.transaction();
    }

    public void handleBlockChange(CultPlayer player, PacketSendEvent<BlockUpdate> event, BlockUpdate blockChange) {
        int range = 16;

        BlockPos blockPosition = blockChange.position();
        BlockState state = blockChange.state();
        // MCP-Reborn ClientPacketListener handles packets in bundle order on the client
        // thread. Put Cult's ping after the block update so the pong marks the point
        // where the client has processed the new block state.
        if (isNearPlayer(player, blockPosition, range)) {
            CultPlayer.TrackedTransaction transaction = player.createTrackedTransactionPacketForBundle();
            if (transaction != null) {
                if (blockChange != event.getOriginalPacket()) event.replace(blockChange);
                event.getWritesAfterSend().add(transaction.packet());
                event.getTasksAfterSend().add(() -> player.markTrackedTransactionPacketSent(transaction));
                player.compensatedWorld.handleServerBlockUpdate(blockPosition, state, transaction);
                return;
            }
        }

        player.latencyUtils.addRealTimeTaskNow(() -> player.compensatedWorld.handleServerBlockUpdate(
                blockPosition, state, player.lastTransactionSent.get()));
    }

    public void handleBlockEvent(
            CultPlayer player, PacketSendEvent<ClientboundBlockEvent> event, ClientboundBlockEvent blockEvent) {
        int range = 16;
        BlockPos blockPosition = new BlockPos(
                blockEvent.position().x(),
                blockEvent.position().y(),
                blockEvent.position().z());
        if (isNearPlayer(player, blockPosition, range)) {
            CultPlayer.TrackedTransaction transaction = player.createTrackedTransactionPacketForBundle();
            if (transaction != null) {
                if (blockEvent != event.getOriginalPacket()) event.replace(blockEvent);
                event.getWritesAfterSend().add(transaction.packet());
                event.getTasksAfterSend().add(() -> {
                    player.markTrackedTransactionPacketSent(transaction);
                    Runnable applyEvent = () -> player.compensatedWorld.pistons.handleBlockEvent(
                            blockPosition,
                            BuiltInRegistries.BLOCK.byId(blockEvent.blockId()),
                            blockEvent.action(),
                            blockEvent.parameter(),
                            transaction.transaction());
                    player.latencyUtils.addRealTimeTask(transaction.transaction(), applyEvent);
                });
                return;
            }
        }

        player.latencyUtils.addRealTimeTaskNow(() -> player.compensatedWorld.pistons.handleBlockEvent(
                blockPosition,
                BuiltInRegistries.BLOCK.byId(blockEvent.blockId()),
                blockEvent.action(),
                blockEvent.parameter(),
                player.lastTransactionSent.get()));
    }

    public void handleMultiBlockChange(
            CultPlayer player, PacketSendEvent<SectionBlocksUpdate> event, SectionBlocksUpdate multiBlockChange) {
        int range = 16;
        List<BlockUpdate> updates = multiBlockChange.updates();
        boolean nearPlayer = updates.stream().anyMatch(update -> isNearPlayer(player, update.position(), range));

        if (updates.isEmpty()) {
            return;
        }

        if (nearPlayer) {
            CultPlayer.TrackedTransaction transaction = player.createTrackedTransactionPacketForBundle();
            if (transaction != null) {

                event.getWritesAfterSend().add(transaction.packet());
                event.getTasksAfterSend().add(() -> player.markTrackedTransactionPacketSent(transaction));
                player.latencyUtils.addRealTimeTask(
                        transaction.transaction(),
                        () -> updates.forEach(update -> player.compensatedWorld.handleServerBlockUpdate(
                                update.position(), update.state(), transaction.transaction())));
                return;
            }
        }

        player.latencyUtils.addRealTimeTaskNow(
                () -> updates.forEach(update -> player.compensatedWorld.handleServerBlockUpdate(
                        update.position(), update.state(), player.lastTransactionSent.get())));
    }

    private boolean isNearPlayer(CultPlayer player, BlockPos pos, int range) {
        return Math.abs(pos.getX() - player.x) < range
                && Math.abs(pos.getY() - player.y) < range
                && Math.abs(pos.getZ() - player.z) < range;
    }
}
