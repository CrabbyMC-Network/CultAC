package ac.cult.cultac.checks.impl.badpackets;

import ac.cult.cultac.protocol.packet.serverbound.ServerboundSpectatorAction;

import ac.cult.cultac.checks.Check;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.checks.CheckData;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundInteract;

@CheckData(name = "BadPacketsW", stableKey = "cult.badpackets.invalid_entity_target", description = "Interacted with non-existent entity", experimental = true)
public class BadPacketsW extends Check implements CheckListener {
    public BadPacketsW(CultPlayer player) {
        super(player);
    }

    @CultPacketHandler
    public void onInteract(PacketReceiveEvent<ServerboundInteract> event, CultPlayer player, ServerboundInteract packet) {
        validateTarget(event, packet);
    }



    @CultPacketHandler
    public void onSpectatorAction(PacketReceiveEvent<ServerboundSpectatorAction> event, CultPlayer player, ServerboundSpectatorAction packet) {
        packet.target().ifPresent(entityId -> {
            if (isInvalidEntityTarget(entityId)) {
                handleInvalidTarget(event, entityId);
            }
        });
    }

    public void handleInvalidTarget(PacketReceiveEvent event, int entityId) {
        if (flag("entityId=" + entityId) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    private void validateTarget(PacketReceiveEvent event, ServerboundInteract interact) {
        if (interact != null && isInvalidEntityTarget(interact.entityId())) {
            handleInvalidTarget(event, interact.entityId());
        }
    }

    private boolean isInvalidEntityTarget(int entityId) {
        if (player.compensatedEntities.entityMap.containsKey(entityId)
                || player.compensatedEntities.serverPositionsMap.containsKey(entityId)) {
            return false;
        }

        // Pre-1.14 clients may still target an entity removed during this server
        // transaction because their ray-trace entity list updates later.
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)
                || !player.packetEntityReplication.wasDespawnedThisTransaction(entityId);
    }
}
