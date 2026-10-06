package ac.cult.cultac.bridge.wire;

import org.junit.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class BridgeEnvelopeCodecTest {
    private final byte[] key = new byte[32];
    private final UUID player = UUID.randomUUID(), connection = UUID.randomUUID();
    private final BridgeEnvelopeCodec codec = new BridgeEnvelopeCodec(key);
    private BridgeEnvelope message(long sequence) {
        return new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND, BridgeEnvelope.Kind.CLIENT_PACKET,
                player, connection, sequence, new byte[]{10,20,30});
    }
    private void rejected(byte[] packet) {
        try { codec.decode(packet); fail("Rejected frame accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private byte[] sign(byte[] packet) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
        byte[] tag = mac.doFinal(Arrays.copyOf(packet,packet.length-32));
        System.arraycopy(tag,0,packet,packet.length-32,32); return packet;
    }
    @Test public void roundTripRetainsSessionAndPayload() {
        var input = message(1); var result = codec.decode(codec.encode(input));
        assertEquals(input.player(),result.player()); assertEquals(input.connection(),result.connection());
        assertEquals(input.kind(),result.kind()); assertEquals(1,result.sequence());
        assertArrayEquals(input.body(),result.body());
    }
    @Test public void payloadAndKeyAreDefensivelyCopied() {
        byte[] body={1}; var envelope=new BridgeEnvelope(BridgeEnvelope.Direction.TO_GATEWAY,
                BridgeEnvelope.Kind.INPUT_RESULT,player,connection,1,body);
        body[0]=9; envelope.body()[0]=8; assertEquals(1,envelope.body()[0]);
        byte[] encoded=codec.encode(envelope); key[0]=1;
        assertArrayEquals(encoded,codec.encode(envelope));
    }
    @Test public void alteredBodyIsRejected() { byte[] p=codec.encode(message(1)); p[p.length-33]^=1; rejected(p); }
    @Test public void alteredHeaderIsRejected() { byte[] p=codec.encode(message(1)); p[10]^=1; rejected(p); }
    @Test public void wrongKeyIsRejected() {
        byte[] other=new byte[32]; other[0]=1;
        try { new BridgeEnvelopeCodec(other).decode(codec.encode(message(1))); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void truncatedAndTrailingFramesAreRejected() {
        byte[] p=codec.encode(message(1)); rejected(Arrays.copyOf(p,p.length-1)); rejected(Arrays.copyOf(p,p.length+1));
    }
    @Test public void unsupportedSignedVersionIsRejected() throws Exception {
        byte[] p=codec.encode(message(1));p[4]=2;rejected(sign(p));
    }
    @Test public void unknownSignedKindIsRejected() throws Exception {
        byte[] p=codec.encode(message(1));p[6]=(byte)127;rejected(sign(p));
    }
    @Test public void invalidSignedLengthIsRejected() throws Exception {
        byte[] p=codec.encode(message(1));p[50]=4;rejected(sign(p));
    }
    @Test public void boundsPreventOversizedAllocations() {
        try { new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.CLIENT_PACKET,
                player,connection,1,new byte[BridgeEnvelope.MAX_BODY_BYTES+1]);fail(); }
        catch (IllegalArgumentException expected) { }
        rejected(new byte[BridgeEnvelope.MAX_BODY_BYTES+100]);
    }
    @Test public void sequenceDuplicatesAndGapsCannotBeAccepted() {
        var order=new BridgeReceiptOrder(player,connection,BridgeEnvelope.Direction.TO_BACKEND,1);
        assertFalse(order.accept(message(2)));assertTrue(order.accept(message(1)));
        assertFalse(order.accept(message(1)));assertTrue(order.accept(message(2)));
    }
    @Test public void oldConnectionOtherPlayerAndDirectionCannotCrossSessions() {
        var order=new BridgeReceiptOrder(player,connection,BridgeEnvelope.Direction.TO_BACKEND,1);
        assertFalse(order.accept(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.CLIENT_PACKET,
                player,UUID.randomUUID(),1,new byte[0])));
        assertFalse(order.accept(new BridgeEnvelope(BridgeEnvelope.Direction.TO_BACKEND,BridgeEnvelope.Kind.CLIENT_PACKET,
                UUID.randomUUID(),connection,1,new byte[0])));
        assertFalse(order.accept(new BridgeEnvelope(BridgeEnvelope.Direction.TO_GATEWAY,BridgeEnvelope.Kind.CLIENT_PACKET,
                player,connection,1,new byte[0])));
        assertTrue(order.accept(message(1)));order.close();assertFalse(order.accept(message(2)));
    }
    @Test public void concurrentDuplicateHasExactlyOneWinner() throws Exception {
        var order=new BridgeReceiptOrder(player,connection,BridgeEnvelope.Direction.TO_BACKEND,1);
        var start=new CountDownLatch(1);var winners=new AtomicInteger();var threads=new Thread[8];
        for(int i=0;i<threads.length;i++) { threads[i]=new Thread(()->{
            try {start.await();if(order.accept(message(1)))winners.incrementAndGet();}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
        });threads[i].start(); }
        start.countDown();for(Thread thread:threads)thread.join();assertEquals(1,winners.get());
    }
    @Test public void maximumSequenceClosesWithoutOverflow() {
        var order=new BridgeReceiptOrder(player,connection,BridgeEnvelope.Direction.TO_BACKEND,Long.MAX_VALUE);
        assertTrue(order.accept(message(Long.MAX_VALUE)));assertFalse(order.accept(message(Long.MAX_VALUE)));
    }
}
