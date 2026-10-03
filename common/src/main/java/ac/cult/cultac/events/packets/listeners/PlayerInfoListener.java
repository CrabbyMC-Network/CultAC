package ac.cult.cultac.events.packets.listeners;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.CultWrite;
import ac.cult.cultac.network.event.PacketSendEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerInfoUpdate;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerInfoUpdate.Action;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundPlayerInfoUpdate.Entry;
import ac.cult.cultac.protocol.value.GameMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PlayerInfoListener {
    @CultPacketHandler
    public void onPlayerInfoUpdate(
            PacketSendEvent<ClientboundPlayerInfoUpdate> event,
            CultPlayer receiver,
            ClientboundPlayerInfoUpdate packet) {
        if (!packet.actions().contains(Action.UPDATE_GAME_MODE)) return;
        List<Entry> visibleEntries = new ArrayList<>(packet.entries().size());
        for (Entry entry : packet.entries()) {
            boolean hidden = CultAPI.INSTANCE.getSpectateManager().shouldHidePlayer(receiver, entry.profileId());
            if (!(hidden && entry.gameMode() == GameMode.SPECTATOR)) visibleEntries.add(entry);
        }
        if (visibleEntries.size() == packet.entries().size()) return;

        if (packet.actions().size() == 1) {
            if (visibleEntries.isEmpty()) event.setCancelled(true);
            else event.replace(new ClientboundPlayerInfoUpdate(EnumSet.of(Action.UPDATE_GAME_MODE), visibleEntries));
            return;
        }

        EnumSet<Action> strippedActions = EnumSet.copyOf(packet.actions());
        strippedActions.remove(Action.UPDATE_GAME_MODE);
        event.replace(new ClientboundPlayerInfoUpdate(strippedActions, packet.entries()));
        if (!visibleEntries.isEmpty()) {
            var gameModeUpdate = new ClientboundPlayerInfoUpdate(EnumSet.of(Action.UPDATE_GAME_MODE), visibleEntries);
            event.getTasksAfterSend().add(() -> event.getUser().write(new CultWrite(gameModeUpdate, false)));
        }
    }
}
