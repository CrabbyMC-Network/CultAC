package ac.cult.cultac.bridge.wire;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class NativeStateMessageTest {
    @Test public void rejectsNonFiniteMetadataAndAttributeValues(){
        assertThrows(IllegalArgumentException.class,()->new ActorStateMessage.Metadata(Float.NaN,null,null,null,null,null,null,null,null,null,1));
        assertThrows(IllegalArgumentException.class,()->new ActorStateMessage.Attribute("minecraft:movement",Float.POSITIVE_INFINITY,0,1024,0,1024,.1f,List.of()));
        assertThrows(IllegalArgumentException.class,()->new ActorStateMessage.Modifier("id","name",Float.NaN,0,2,true));
        assertThrows(IllegalArgumentException.class,()->new ActorStateMessage.Collision(.6f,0));
    }
    @Test public void nativeStateBoundsAndTrailingBytesAreRejected(){
        var state=new ActorStateMessage(1,23,5,10,ActorStateMessage.Kind.COLLISION,new ActorStateMessage.Collision(.6f,1.8f).encode());
        var bytes=state.encode();var decoded=ActorStateMessage.decode(bytes);assertEquals(state.request(),decoded.request());assertEquals(state.kind(),decoded.kind());
        assertThrows(IllegalArgumentException.class,()->ActorStateMessage.decode(Arrays.copyOf(bytes,bytes.length+1)));
        assertThrows(IllegalArgumentException.class,()->ActorStateMessage.decode(Arrays.copyOf(bytes,bytes.length-1)));
        assertThrows(IllegalArgumentException.class,()->ActorStateMessage.Collision.decode(new byte[32]));
    }
    @Test public void fullAttributesKeepModifiersAndEveryRangeValue(){
        var source=new ActorStateMessage.Attributes(List.of(new ActorStateMessage.Attribute("minecraft:movement",.13f,0,1024,0,1024,.1f,List.of(new ActorStateMessage.Modifier("sprint","boost",.3f,2,2,true)))));
        assertEquals(source,ActorStateMessage.Attributes.decode(source.encode()));
    }
    @Test public void teleportEmissionRetainsOwnershipAndExactFloatEcho(){
        var source=new TeleportEmissionMessage(3,17,1,25,new AuthInputMessage.Double3(12.5,64,-9),new AuthInputMessage.Float3(12.5f,65.62001f,-9),90,0,true,43,0,0,0);
        assertEquals(source,TeleportEmissionMessage.decode(source.encode()));
        assertThrows(IllegalArgumentException.class,()->TeleportEmissionMessage.decode(Arrays.copyOf(source.encode(),source.encode().length+1)));
    }
}
