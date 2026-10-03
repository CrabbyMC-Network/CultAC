package ac.cult.cultac.network;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketDirection;
import ac.cult.cultac.protocol.ProtocolRuntime;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ProtocolData;
import ac.cult.cultac.protocol.netty.CultDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.junit.jupiter.api.Test;

class HandshakeReentryTest {
    @Test
    void nativeHandshakeReadResumeCannotDecodeLoginBeforeCultFinishesTheHandshake() {
        var runtime = ProtocolRuntime.create(ProtocolData.load(ProtocolVersion.V26_3));
        var routes = new TestTransportRoutes(runtime);
        var channel = new EmbeddedChannel();
        var connection = new CultConnection(channel, routes.dispatcher, ignored -> null);
        var hello = new ServerboundHelloPacket("Define_Outside", UUID.randomUUID());
        ByteBuf login = Unpooled.buffer();
        LoginProtocols.SERVERBOUND.codec().encode(new FriendlyByteBuf(login), hello);
        var delivered = new AtomicBoolean();
        // LocalChannel can deliver its next buffered frame synchronously in read().
        channel.pipeline().addLast("local_read", new ChannelDuplexHandler() {
            @Override
            public void read(ChannelHandlerContext ctx) {
                if (delivered.compareAndSet(false, true)) ctx.fireChannelRead(login);
            }
        });
        channel.pipeline().addLast("decoder", new PacketDecoder<>(HandshakeProtocols.SERVERBOUND));
        channel.pipeline().addLast("listener", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object packet) {
                if (packet instanceof ClientIntentionPacket) {
                    // ServerHandshakePacketListenerImpl.beginLogin installs this codec;
                    // UnconfiguredPipelineHandler then enables auto-read before returning.
                    ctx.writeAndFlush(UnconfiguredPipelineHandler.setupInboundProtocol(LoginProtocols.SERVERBOUND));
                } else ctx.fireChannelRead(packet);
            }
        });
        CultDecoder.install(connection);
        ByteBuf handshake = Unpooled.buffer();
        HandshakeProtocols.SERVERBOUND
                .codec()
                .encode(
                        new FriendlyByteBuf(handshake),
                        new ClientIntentionPacket(777, "localhost", 25565, ClientIntent.LOGIN));
        try {
            assertTrue(channel.writeInbound(handshake));
            assertTrue(delivered.get());
            assertEquals(hello, channel.readInbound());
            assertNull(channel.readInbound());
            assertEquals(ConnectionPhase.LOGIN, connection.phase(PacketDirection.SERVERBOUND));
            assertEquals(ConnectionPhase.LOGIN, connection.phase(PacketDirection.CLIENTBOUND));
            assertTrue(channel.isActive());
            assertEquals(0, handshake.refCnt());
            assertEquals(0, login.refCnt());
        } finally {
            channel.finishAndReleaseAll();
            if (!delivered.get()) login.release();
        }
    }
}
