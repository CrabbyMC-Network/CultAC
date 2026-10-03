package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundKeepAlive;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundKeepAlive;
import ac.grim.grimac.api.storage.verbose.Verbose;
import java.util.LinkedList;

@CheckData(
        name = "BadPacketsO",
        stableKey = "cult.badpackets.invalid_keepalive",
        description = "Responded with a keepalive ID that was not sent by the server")
public class BadPacketsO extends Check implements CheckListener {
    private static final Verbose V = Verbose.of("id={slong}");
    private final LinkedList<Long> keepalives = new LinkedList<>();

    public BadPacketsO(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onKeepAlive(
            PacketSendEvent<ClientboundKeepAlive> event, CultPlayer player, ClientboundKeepAlive packet) {
        keepalives.add(packet.id());
    }

    @CultPacketHandler
    public void onKeepAlive(
            PacketReceiveEvent<ServerboundKeepAlive> event, CultPlayer player, ServerboundKeepAlive packet) {
        long id = packet.id();
        for (long keepalive : keepalives) {
            if (keepalive == id) {
                Long data;
                do {
                    data = keepalives.poll();
                } while (data != null && data != id);
                return;
            }
        }

        handleInvalidKeepAlive(event, id);
    }

    private void handleInvalidKeepAlive(PacketReceiveEvent event, long id) {
        if (flag(V.write(verbose()).slong(id)) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
