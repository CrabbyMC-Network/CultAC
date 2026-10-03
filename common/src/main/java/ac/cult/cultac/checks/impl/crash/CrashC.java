package ac.cult.cultac.checks.impl.crash;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;

@CheckData(name = "CrashC", stableKey = "cult.crash.nan_position", description = "Sent non-finite position or rotation")
public class CrashC extends Check implements CheckListener {
    private static final Verbose V =
            Verbose.of("xyzYP={f64}, {f64}, {f64}, {f32}, {f32}");

    public CrashC(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler

    public void onMovePlayer(PacketReceiveEvent<ServerboundMovePlayer> event, CultPlayer player, ServerboundMovePlayer packet) {
        if (packet.hasPosition()) {
            double x = packet.xOr(0);
            double y = packet.yOr(0);
            double z = packet.zOr(0);
            float yaw = packet.yawOr(0);
            float pitch = packet.pitchOr(0);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                flag(V.write(verbose()).f64(x).f64(y).f64(z).f32(yaw).f32(pitch));
                executeViolationSetback();
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }
    }
}
