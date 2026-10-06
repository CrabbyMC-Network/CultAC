package ac.cult.cultac.bedrock.replay.offline;

import ac.cult.cultac.bedrock.protocol.*;
import ac.cult.cultac.network.protocol.teleport.RelativeFlag;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProxyBridgeTeleportReceiptTest {
    @Test public void identityOriginAndSyntheticJavaProofCannotReplaceNativeReceipt() {
        OfflineCultTestBootstrap.installConfig();
        var player = OfflineBedrockReplayRunnerTest.offlinePlayer();
        try {
            var teleports = player.getSetbackTeleportUtil();
            Vec3 feet = new Vec3(12.5, 82, -.5);
            Vec3 eye = feet.add(0, 1.6200103759765625, 0);
            teleports.addSentTeleport(feet, 10, new RelativeFlag(0), false, 0);
            teleports.pendingTeleports.clear();
            var operation = new BedrockTeleportOperation(1, BedrockTeleportProvenance.CULT_SETBACK, 10);
            long revision = teleports.addImmediateBedrockTransportTeleport(feet, true,
                BedrockCoordinateFrame.IDENTITY, eye, operation, 10);
            teleports.requireBedrockTransportReceipt(revision);
            player.lastTransactionReceived.set(10);
            var input = BedrockAuthInputFrame.builder(player.playerUUID).clientTick(100).position(feet)
                .packetPosition(eye).rawInputFlags(1L << PlayerAuthInputData.HANDLE_TELEPORT.ordinal()).build();
            assertFalse(teleports.acknowledgeBedrockTeleportFrame(input).isTeleport());
            assertTrue(teleports.hasPendingBedrockTransportTeleport());
            teleports.confirmBedrockOrigin(revision, operation, BedrockCoordinateFrame.IDENTITY, eye.add(1, 0, 0));
            assertFalse(teleports.acknowledgeBedrockTeleportFrame(input).isTeleport());
            teleports.confirmBedrockOrigin(revision, operation, BedrockCoordinateFrame.IDENTITY, eye);
            assertTrue(teleports.acknowledgeBedrockTeleportFrame(input).isTeleport());
            assertFalse(teleports.hasPendingBedrockTransportTeleport());
        } finally { OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player); }
    }
}
