package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.bridge.wire.*;
import ac.cult.cultac.network.protocol.player.User;
import java.util.*;
import java.util.function.Consumer;

/** One authenticated lease per exact backend User, invalidated by disconnect/transfer. */
public final class ProxyBridgeSessions {
    private final BridgeEnvelopeCodec codec;
    private final Map<User,Session> sessions=new IdentityHashMap<>();
    public ProxyBridgeSessions(byte[] key){codec=new BridgeEnvelopeCodec(key);}
    public synchronized Session open(User owner,Consumer<byte[]> transport) {
        close(owner);
        var session=new Session(owner,UUID.randomUUID(),transport,codec);
        sessions.put(owner,session);
        session.send(BridgeEnvelope.Kind.CHALLENGE,new byte[0]);
        return session;
    }
    public synchronized Session get(User owner){return sessions.get(owner);}
    public synchronized void close(User owner){var old=sessions.remove(owner);if(old!=null)old.close();}
    public synchronized void closeAll(){sessions.values().forEach(Session::close);sessions.clear();}
    public static final class Session {
        private final User owner;
        private final UUID nonce;
        private final BridgeEnvelopeCodec codec;
        private final BridgeReceiptOrder receipts;
        private final Consumer<byte[]> transport;
        private long nextSequence;
        private boolean closed,hello;
        private BridgeControlMessage.Hello context;
        private Session(User owner,UUID nonce,Consumer<byte[]> transport,BridgeEnvelopeCodec codec) {
            this.owner=Objects.requireNonNull(owner);this.nonce=nonce;this.transport=Objects.requireNonNull(transport);this.codec=codec;
            receipts=new BridgeReceiptOrder(owner.getUUID(),nonce,BridgeEnvelope.Direction.TO_BACKEND,0);
        }
        public User owner(){return owner;}
        public BridgeControlMessage.Hello context(){return context;}
        public synchronized BridgeEnvelope receive(User actual,byte[] bytes) {
            if(closed || actual!=owner)throw new IllegalArgumentException("Expired bridge connection");
            try {
            BridgeEnvelope envelope=codec.decode(bytes);
            // Validate hello before consuming its slot. Any protocol failure closes the lease.
            if(!hello && envelope.kind()!=BridgeEnvelope.Kind.HELLO)throw new IllegalArgumentException("Bridge handshake required");
            if(hello && envelope.kind()==BridgeEnvelope.Kind.HELLO)throw new IllegalArgumentException("Duplicate bridge handshake");
            BridgeControlMessage.Hello observed=envelope.kind()==BridgeEnvelope.Kind.HELLO?BridgeControlMessage.Hello.decode(envelope.body()):null;
            if(!receipts.accept(envelope))throw new IllegalArgumentException("Invalid bridge receipt order");
            if(observed!=null){context=observed;hello=true;}
            return envelope;
            } catch (RuntimeException failure) { close(); throw failure; }
        }
        public synchronized byte[] encode(BridgeEnvelope.Kind kind,byte[] body) {
            if(closed || nextSequence==Long.MAX_VALUE)throw new IllegalStateException("Expired bridge connection");
            return codec.encode(new BridgeEnvelope(BridgeEnvelope.Direction.TO_GATEWAY,kind,owner.getUUID(),nonce,nextSequence++,body));
        }
        public synchronized void send(BridgeEnvelope.Kind kind,byte[] body) {transport.accept(encode(kind,body));}
        public synchronized void close(){closed=true;receipts.close();}
    }
}
