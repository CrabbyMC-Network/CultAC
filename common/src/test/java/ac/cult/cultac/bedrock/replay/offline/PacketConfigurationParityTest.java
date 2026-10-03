package ac.cult.cultac.bedrock.replay.offline;

import ac.cult.cultac.checks.impl.chat.ChatD;
import ac.cult.cultac.events.packets.listeners.PacketConfigurationListener;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.UUID;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundCustomPayload;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundClientInformation;
import ac.cult.cultac.protocol.value.ClientInformation;
import ac.cult.cultac.protocol.value.ChatVisibility;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class PacketConfigurationParityTest {
    @Test
    public void configurationListenerDoesNotHandlePlayCustomPayload() {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try {
            var packet = new ServerboundCustomPayload("cult:play_only", new byte[0]);
            var event = RecordReceiveTestEvents.customPayload(player, packet);

            new PacketConfigurationListener().onCustomPayload(event, player, packet);

            assertTrue(player.pluginChannelManager.getRegisteredChannels().isEmpty());
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    @Test
    public void configurationClientInformationUpdatesChatDVisibility() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try {
            ClientInformation defaults = defaults();
            ClientInformation hidden = new ClientInformation(
                    defaults.language(),
                    defaults.viewDistance(),
                    ChatVisibility.HIDDEN,
                    defaults.chatColors(),
                    defaults.modelCustomisation(),
                    defaults.mainHand(),
                    defaults.textFilteringEnabled(),
                    defaults.allowsListing(),
                    defaults.particleStatus());
            ServerboundClientInformation packet = new ServerboundClientInformation(hidden);
            var event = RecordReceiveTestEvents.clientInformation(player, packet, ac.cult.cultac.protocol.ConnectionPhase.CONFIGURATION);

            new PacketConfigurationListener().onClientInformation(event, player, packet);

            assertTrue(booleanField(player.checkManager.getListener(ChatD.class), "hidden"));
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    @Test
    public void configurationClientInformationIsNotSanitizedByPlayOnlyCrashE() {
        OfflineCultTestBootstrap.installConfig();
        CultPlayer player = offlineJavaPlayer();
        try {
            ClientInformation defaults = defaults();
            ClientInformation lowDistance = new ClientInformation(
                    defaults.language(),
                    1,
                    defaults.chatVisibility(),
                    defaults.chatColors(),
                    defaults.modelCustomisation(),
                    defaults.mainHand(),
                    defaults.textFilteringEnabled(),
                    defaults.allowsListing(),
                    defaults.particleStatus());
            ServerboundClientInformation packet = new ServerboundClientInformation(lowDistance);
            var event = RecordReceiveTestEvents.clientInformation(player, packet, ac.cult.cultac.protocol.ConnectionPhase.CONFIGURATION);

            new PacketConfigurationListener().onClientInformation(event, player, packet);

            assertTrue(event.getPacket() == packet);
            assertTrue(event.getOriginalPacket() == packet);
        } finally {
            OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player);
        }
    }

    private static ClientInformation defaults() {
        return new ClientInformation("en_us", 2, ChatVisibility.FULL, true, 0,
                ac.cult.cultac.protocol.value.MainHand.RIGHT, false, false, ac.cult.cultac.protocol.value.ParticleStatus.ALL);
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static CultPlayer offlineJavaPlayer() {
        UUID playerId = UUID.fromString("244cd32d-3e42-4ec0-b22c-c95be54d1c58");
        User user = ac.cult.cultac.network.TestUsers.create(new User.Profile(playerId, ".Configuration_Test"), new EmbeddedChannel());
        return new CultPlayer(user);
    }
}
