package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.DeadCheck;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.player.CultPlayer;

@CheckData(
        name = "BadPacketsS",
        stableKey = "cult.badpackets.window_confirmation_not_accepted",
        description = "Sent a window confirmation packet marked as not accepted")
@DeadCheck(
        reason = DeadCheck.Reason.WIRE_UNTRIGGERABLE,
        detail =
                "No serverbound container confirmation exists in the supported server protocols (1.21.3 through 26.3).")
public class BadPacketsS extends Check implements CheckListener {
    public BadPacketsS(CultPlayer player) {
        super(player);
    }
}
