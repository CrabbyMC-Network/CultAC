package ac.cult.cultac.bedrock.replay.offline;

import ac.cult.cultac.protocol.packet.Opaque;

import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.packet.ServerboundPackets;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPong;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerCommand;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerInput;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;
import ac.cult.cultac.protocol.ConnectionPhase;

import static org.mockito.Mockito.*;

/** Record events for state-level tests; byte/native parity is exercised separately. */
final class RecordReceiveTestEvents {
    private RecordReceiveTestEvents() { }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundKeepAlive> keepAlive(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundKeepAlive packet) {
        return record(player, ServerboundPackets.KEEP_ALIVE, "minecraft:keep_alive", packet);
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.Opaque> signUpdate(CultPlayer player) {
        return record(player, ServerboundPackets.SIGN_UPDATE, "minecraft:sign_update",
                ac.cult.cultac.protocol.packet.ServerboundPackets.SIGN_UPDATE.opaqueValue());
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAction> playerAction(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAction packet) {
        return record(player, ServerboundPackets.PLAYER_ACTION, "minecraft:player_action", packet);
    }

    static PacketReceiveEvent<ServerboundPlayerCommand> playerCommand(CultPlayer player, ServerboundPlayerCommand packet) {
        return record(player, ServerboundPackets.PLAYER_COMMAND, "minecraft:player_command", packet);
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundInteract> attack(CultPlayer player, int entityId) {
        return record(player, ServerboundPackets.INTERACT, "minecraft:attack",
                new ac.cult.cultac.protocol.packet.serverbound.ServerboundInteract(entityId,
                        ac.cult.cultac.protocol.value.InteractAction.ATTACK, ac.cult.cultac.protocol.value.Hand.MAIN_HAND,
                        java.util.Optional.empty(), false));
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundMoveVehicle> vehicle(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundMoveVehicle packet) {
        return record(player, ServerboundPackets.MOVE_VEHICLE, "minecraft:move_vehicle", packet);
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundAcceptTeleportation> teleport(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundAcceptTeleportation packet) {
        return record(player, ServerboundPackets.ACCEPT_TELEPORTATION, "minecraft:accept_teleportation", packet);
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundSwing> swing(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundSwing packet) {
        return record(player, ServerboundPackets.SWING, "minecraft:swing", packet);
    }

    static PacketReceiveEvent<ServerboundPong> pong(CultPlayer player, int id) {
        return record(player, ServerboundPackets.PONG, "minecraft:pong", new ServerboundPong(id));
    }

    static PacketReceiveEvent<ServerboundPlayerInput> input(CultPlayer player, ServerboundPlayerInput input) {
        return record(player, ServerboundPackets.PLAYER_INPUT, "minecraft:player_input", input);
    }

    static PacketReceiveEvent<Opaque> tickEnd(CultPlayer player) {
        return record(player, ServerboundPackets.CLIENT_TICK_END, "minecraft:client_tick_end", ac.cult.cultac.protocol.packet.ServerboundPackets.CLIENT_TICK_END.opaqueValue());
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundCustomPayload> customPayload(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundCustomPayload packet) {
        return record(player, ServerboundPackets.CUSTOM_PAYLOAD, "minecraft:custom_payload", packet);
    }

    static PacketReceiveEvent<ServerboundMovePlayer> movement(CultPlayer player, ServerboundMovePlayer packet) {
        String variant = packet.hasPosition() ? (packet.hasRotation() ? "pos_rot" : "pos")
                : (packet.hasRotation() ? "rot" : "status_only");
        return record(player, ServerboundPackets.MOVE_PLAYER, "minecraft:move_player_" + variant, packet);
    }

    static PacketReceiveEvent<ac.cult.cultac.protocol.packet.serverbound.ServerboundClientInformation> clientInformation(
            CultPlayer player, ac.cult.cultac.protocol.packet.serverbound.ServerboundClientInformation packet, ConnectionPhase phase) {
        return record(player, ServerboundPackets.CLIENT_INFORMATION, "minecraft:client_information", packet, phase);
    }

    private static <T extends ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket> PacketReceiveEvent<T> record(
            CultPlayer player, PacketType<T> type, String name, T packet) {
        return record(player, type, name, packet, ConnectionPhase.PLAY);
    }

    private static <T extends ac.cult.cultac.protocol.packet.serverbound.ServerboundPacket> PacketReceiveEvent<T> record(
            CultPlayer player, PacketType<T> type, String name, T packet, ConnectionPhase phase) {
        return new PacketReceiveEvent<>(player.user, phase, type, packet);
    }
}
