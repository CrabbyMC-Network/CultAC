package ac.cult.cultac.events.packets.listeners;

import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.player.CultPlayer.TrackedTransaction;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundCooldown;

public class PacketPlayerCooldown {
    // HIGH
    @CultPacketHandler
    public void onCooldown(PacketSendEvent<ClientboundCooldown> event, CultPlayer player, ClientboundCooldown packet) {
        String group = packet.group();

        int proofTransaction = player.lastTransactionSent.get();
        TrackedTransaction trackedTransaction = player.createTrackedTransactionPacketForBundle();
        if (trackedTransaction != null) {
            proofTransaction = trackedTransaction.transaction();
            TrackedTransaction sentTransaction = trackedTransaction;
            event.getTasksAfterSend().add(() -> {
                player.user.write(sentTransaction.packet());
                player.markTrackedTransactionPacketSent(sentTransaction);
            });
        }

        int lastTransactionSent = proofTransaction;
        int duration = packet.duration();
        player.latencyUtils.addRealTimeTask(
                lastTransactionSent,
                () -> player.checkManager.getCompensatedCooldown().addCooldown(group, duration, lastTransactionSent));
    }
}
