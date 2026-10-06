package ac.cult.cultac.bedrock.bridge;
import ac.cult.cultac.bridge.wire.*;
import ac.cult.cultac.network.protocol.player.User;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
class ProxyBridgeSessionsTest {
    private static final byte[] KEY=new byte[32];
    private static User user(UUID uuid){return new User(new User.Profile(uuid,"test"),null,null,null,new EmbeddedChannel());}
    private static BridgeEnvelope hello(BridgeEnvelope challenge){return new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.HELLO,challenge.player(),challenge.connection(),0,new BridgeControlMessage.Hello(924,123,1,.6f,1.8f).encode());}
    @Test void authenticHelloRequiresExactUserAndChallengeNonce(){
        var codec=new BridgeEnvelopeCodec(KEY);var sessions=new ProxyBridgeSessions(KEY);var u=user(UUID.randomUUID());var outputs=new ArrayList<byte[]>();
        var session=sessions.open(u,outputs::add);var challenge=codec.decode(outputs.getFirst());
        assertEquals(BridgeEnvelope.Kind.CHALLENGE,challenge.kind());assertNotNull(session.receive(u,codec.encode(hello(challenge))));
        assertEquals(123,session.context().actorRuntimeId());
        assertThrows(IllegalArgumentException.class,()->session.receive(user(u.getUUID()),codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.CLIENT_PACKET,challenge.player(),challenge.connection(),1,new byte[0]))));
    }
    @Test void reconnectRejectsOldNonceEvenWithSameUuid(){
        var codec=new BridgeEnvelopeCodec(KEY);var sessions=new ProxyBridgeSessions(KEY);var uuid=UUID.randomUUID();var u=user(uuid);var outputs=new ArrayList<byte[]>();
        var old=sessions.open(u,outputs::add);var oldHello=hello(codec.decode(outputs.getFirst()));sessions.close(u);
        assertThrows(IllegalArgumentException.class,()->old.receive(u,codec.encode(oldHello)));
        var next=user(uuid);var newer=sessions.open(next,outputs::add);
        assertThrows(IllegalArgumentException.class,()->newer.receive(next,codec.encode(oldHello)));
    }
    @Test void duplicateOrOutOfOrderMessageClosesLease(){
        var codec=new BridgeEnvelopeCodec(KEY);var sessions=new ProxyBridgeSessions(KEY);var u=user(UUID.randomUUID());var outputs=new ArrayList<byte[]>();
        var session=sessions.open(u,outputs::add);var h=hello(codec.decode(outputs.getFirst()));session.receive(u,codec.encode(h));
        assertThrows(IllegalArgumentException.class,()->session.receive(u,codec.encode(h)));
        assertThrows(IllegalArgumentException.class,()->session.receive(u,codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.CLIENT_PACKET,h.player(),h.connection(),1,new byte[0]))));
    }
    @Test void alteredAuthenticationClosesLease(){
        var codec=new BridgeEnvelopeCodec(KEY);var sessions=new ProxyBridgeSessions(KEY);var u=user(UUID.randomUUID());var outputs=new ArrayList<byte[]>();
        var session=sessions.open(u,outputs::add);var h=hello(codec.decode(outputs.getFirst()));var bytes=codec.encode(h);bytes[bytes.length-1]^=1;
        assertThrows(IllegalArgumentException.class,()->session.receive(u,bytes));assertThrows(IllegalArgumentException.class,()->session.receive(u,codec.encode(h)));
    }
}
