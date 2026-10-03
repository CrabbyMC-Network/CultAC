package ac.cult.cultac.network;

import ac.cult.cultac.protocol.testing.CodecFixture;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.*;
import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.packet.ServerboundPackets;
import ac.cult.cultac.protocol.paper.CultDecoder;
import ac.cult.cultac.protocol.paper.CultEncoder;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class RecordReceiveFixture implements AutoCloseable {
        final ProtocolData data;
        final CultNetworkManager manager = new CultNetworkManager();
        final PacketDispatcher registrar;
        final EmbeddedChannel channel = new EmbeddedChannel();
        final CultConnection connection;
        final CodecFixture codec;
        final User user;
        final CultPlayer player;
        User resolvedUser;

        RecordReceiveFixture(ProtocolVersion version) { this(version, true); }
        RecordReceiveFixture(ProtocolVersion version, boolean createPlayer) {
            OfflineCultTestBootstrap.installConfig();
            data = ProtocolData.load(version);
            var runtime = TestProtocolRuntime.create(data);
            manager.configureTransport(runtime, () -> { }, () -> java.util.concurrent.CompletableFuture.completedFuture(null));
            registrar = manager.dispatcher();
            var routes = registrar;
            codec = new CodecFixture(runtime);
            channel.pipeline().addLast("splitter", new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("decoder", new ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("prepender", new ChannelOutboundHandlerAdapter());
            channel.pipeline().addLast("encoder", new ChannelOutboundHandlerAdapter());
            connection = new CultConnection(org.mockito.Mockito.mock(net.minecraft.network.Connection.class), channel, routes, ignored -> null);
            CultDecoder.install(connection); CultEncoder.install(connection);
            phase(ConnectionPhase.PLAY);
            user = new User(new User.Profile(UUID.randomUUID(), ".Record_User_Test"), connection);
            if (createPlayer) CultAPI.INSTANCE.getPlayerDataManager().addUser(user);
            player = CultAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if (createPlayer) assertNotNull(player);
            resolvedUser = user;

        }
        void phase(ConnectionPhase phase) {
            codec.phase(phase);
            for (var direction : PacketDirection.values()) connection.phase(direction, phase);
        }
        ByteBuf id(PacketType<?> type, String name) {
            ByteBuf frame = Unpooled.buffer();
            int id = data.packets(connection.phase(type.direction()), type.direction()).id("minecraft:" + name);
            assertTrue(id >= 0);
            Wire.writeVarInt(frame, id);
            return frame;
        }
        ByteBuf pong(int id) { return id(ServerboundPackets.PONG, "pong").writeInt(id); }
        <R> ByteBuf encode(PacketType<R> type, R packet) {
            ByteBuf frame = Unpooled.buffer();
            codec.write(type, packet, frame);
            return frame;
        }
        void forward(ByteBuf frame) {
            assertTrue(channel.writeInbound(frame));
            assertSame(frame, channel.readInbound());
            frame.release();
        }
        @Override public void close() {
            CultAPI.INSTANCE.getPlayerDataManager().onDisconnect(user);
            channel.finishAndReleaseAll();
        }
    }
