package ac.cult.cultac.checks.impl.packetorder;

import ac.cult.cultac.protocol.packet.serverbound.ServerboundClientCommand;

import ac.cult.cultac.protocol.value.PlayerAction;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.PostPredictionListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.anticheat.update.PredictionComplete;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAction;

import java.util.ArrayDeque;

@CheckData(name = "PacketOrderL", stableKey = "cult.packetorder.drop_item_order", description = "Sent drop, inventory open, or offhand swap packets in an invalid order", experimental = true)
public class PacketOrderL extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("[inventory|swap]");

    static final int ACTION_INVENTORY = 0;
    static final int ACTION_SWAP = 1;

    public PacketOrderL(final CultPlayer player) {
        super(player);
    }

    private final ArrayDeque<Integer> flags = new ArrayDeque<>();


    @CultPacketHandler
    public void onClientCommand(PacketReceiveEvent<ServerboundClientCommand> event, CultPlayer player, ServerboundClientCommand packet) {
        // The 26.2 enum has no OPEN_INVENTORY_ACHIEVEMENT (removed in 1.12)
        if (!packet.action().name().equals("OPEN_INVENTORY_ACHIEVEMENT")) return;

        if (player.packetOrderProcessor.isDropping()) {
            if (!player.canSkipTicks()) {
                if (flag(V.write(verbose()).bool(true)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            } else {
                flags.add(ACTION_INVENTORY);
            }
        }
    }


    @CultPacketHandler
    public void onPlayerAction(PacketReceiveEvent<ServerboundPlayerAction> event, CultPlayer player, ServerboundPlayerAction packet) {
        if (packet.action() != PlayerAction.SWAP_ITEM_WITH_OFFHAND) return;

        if (player.packetOrderProcessor.isDropping()) {
            if (!player.canSkipTicks()) {
                if (flag(V.write(verbose()).bool(false)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            } else {
                flags.add(ACTION_SWAP);
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (int action : flags) {
                flag(V.write(verbose()).bool(action == ACTION_INVENTORY));
            }
        }

        flags.clear();
    }
}
