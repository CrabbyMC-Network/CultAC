package ac.cult.cultac.bridge.wire;
import java.io.*;
/** Final native actor transform; partial packets are expanded from the gateway's written history. */
public record EntityTransformMessage(AuthInputMessage.Float3 position,AuthInputMessage.Double3 feet,float offset,
        float yaw,float pitch,boolean grounded,boolean teleport,boolean spawn,boolean forceLocal,
        boolean forceCompletion,boolean remove,AuthInputMessage.Float3 motion) {
    public EntityTransformMessage {if(position==null||feet==null||!Float.isFinite(offset)||!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Invalid native entity transform");}
    public byte[] encode(){try{var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);vec(o,position);o.writeDouble(feet.x());o.writeDouble(feet.y());o.writeDouble(feet.z());o.writeFloat(offset);o.writeFloat(yaw);o.writeFloat(pitch);o.writeBoolean(grounded);o.writeBoolean(teleport);o.writeBoolean(spawn);o.writeBoolean(forceLocal);o.writeBoolean(forceCompletion);o.writeBoolean(remove);o.writeBoolean(motion!=null);if(motion!=null)vec(o,motion);return b.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
    public static EntityTransformMessage decode(byte[] b){if(b==null||b.length>256)throw new IllegalArgumentException("Invalid native transform size");try{var i=new DataInputStream(new ByteArrayInputStream(b));var value=new EntityTransformMessage(vec(i),new AuthInputMessage.Double3(i.readDouble(),i.readDouble(),i.readDouble()),i.readFloat(),i.readFloat(),i.readFloat(),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readBoolean()?vec(i):null);if(i.available()!=0)throw new IllegalArgumentException("Trailing transform bytes");return value;}catch(IOException e){throw new IllegalArgumentException("Truncated transform",e);}}
    private static void vec(DataOutputStream o,AuthInputMessage.Float3 v)throws IOException{o.writeFloat(v.x());o.writeFloat(v.y());o.writeFloat(v.z());}
    private static AuthInputMessage.Float3 vec(DataInputStream i)throws IOException{return new AuthInputMessage.Float3(i.readFloat(),i.readFloat(),i.readFloat());}
}
