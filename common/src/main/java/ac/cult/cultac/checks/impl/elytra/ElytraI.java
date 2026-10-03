package ac.cult.cultac.checks.impl.elytra;

import ac.cult.cultac.protocol.value.PlayerCommandAction;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.impl.prediction.PredictionResult;
import ac.cult.cultac.checks.type.PostPredictionListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.anticheat.update.PredictionComplete;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerCommand;

@CheckData(name = "ElytraI", stableKey = "cult.elytra.water", description = "Started gliding in water", experimental = true)
public class ElytraI extends Check implements PostPredictionListener {
    private boolean setback;

    public ElytraI(CultPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion().getProtocolVersion() >= 573; // PE ClientVersion.V_1_15
    }

    @CultPacketHandler
    public void onPlayerCommand(PacketReceiveEvent<ServerboundPlayerCommand> event, CultPlayer player, ServerboundPlayerCommand packet) {
        if (!isApplicable()) return;
        if (packet.action() == PlayerCommandAction.START_FLYING_WITH_ELYTRA
                && wasTouchingWater()
                && flag()) {
            setback = true;
            if (shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
                resyncPose();
            }
        }
    }


    private boolean wasTouchingWater() {
        PredictionResult lastPrediction = player.checkManager.getSimulationProcessor().getLastPrediction();
        return lastPrediction != null
                && lastPrediction.getSimulationContext().getWorldData().getInWater().determinePessimistically();
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!isApplicable()) return;
        if (setback) {
            setback = false;
            setbackIfAboveSetbackVL();
        }
    }

    private void resyncPose() {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14) && player.platformPlayer != null) {
            player.platformPlayer.setSneaking(!player.platformPlayer.isSneaking());
        }
    }
}
