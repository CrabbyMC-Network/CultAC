package ac.cult.cultac.bridge.wire;

import java.io.*;
/** Bounded native-receipt controls. All bytes remain protected by the envelope MAC. */
public final class BridgeControlMessage {
    private BridgeControlMessage() { }
    public record Hello(int protocol,long actorRuntimeId,int authority,float width,float height) {
        public Hello { if(protocol<1 || actorRuntimeId<0 || authority!=1 || !Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0||width>32||height>32) throw new IllegalArgumentException("Invalid actor context"); }
        public byte[] encode(){return write(o->{o.writeInt(protocol);o.writeLong(actorRuntimeId);o.writeInt(authority);o.writeFloat(width);o.writeFloat(height);});}
        public static Hello decode(byte[] b){return read(b,i->new Hello(i.readInt(),i.readLong(),i.readInt(),i.readFloat(),i.readFloat()));}
    }
    public record InputResult(long requestSequence,boolean accepted,AuthInputMessage.Double3 feet,AuthInputMessage.Double3 velocity,
                              boolean horizontalCollision,boolean verticalCollision,boolean grounded,int vehicleJavaId,boolean sprinting,long generation,long tick,boolean actionsConsumed) {
        public InputResult {if(requestSequence<0||feet==null||velocity==null||vehicleJavaId< -1||generation<0||tick<0)throw new IllegalArgumentException("Invalid input result");}
        public byte[] encode(){return write(o->{o.writeLong(requestSequence);o.writeBoolean(accepted);vec(o,feet);vec(o,velocity);o.writeBoolean(horizontalCollision);o.writeBoolean(verticalCollision);o.writeBoolean(grounded);o.writeInt(vehicleJavaId);o.writeBoolean(sprinting);o.writeLong(generation);o.writeLong(tick);o.writeBoolean(actionsConsumed);});}
        public static InputResult decode(byte[] b){return read(b,i->new InputResult(i.readLong(),i.readBoolean(),vec(i),vec(i),i.readBoolean(),i.readBoolean(),i.readBoolean(),i.readInt(),i.readBoolean(),i.readLong(),i.readLong(),i.readBoolean()));}
    }
    public record Latency(int type,long request,int marker,long lastAuthTick) {
        public Latency(int type,long request,int marker){this(type,request,marker,-1);}
        public static final int REQUEST=0,BOUNDARY=1,SENT=2,ACK=3,PING_REGISTER=4;
        public Latency { if(type<0||type>4||request<0||lastAuthTick< -1) throw new IllegalArgumentException("Invalid latency control"); }
        public byte[] encode(){return write(o->{o.writeByte(type);o.writeLong(request);o.writeInt(marker);o.writeLong(lastAuthTick);});}
        public static Latency decode(byte[] b){return read(b,i->new Latency(i.readUnsignedByte(),i.readLong(),i.readInt(),i.readLong()));}
    }
    private static void vec(DataOutputStream o,AuthInputMessage.Double3 v)throws IOException{o.writeDouble(v.x());o.writeDouble(v.y());o.writeDouble(v.z());}
    private static AuthInputMessage.Double3 vec(DataInputStream i)throws IOException{return new AuthInputMessage.Double3(i.readDouble(),i.readDouble(),i.readDouble());}
    private interface Writer{void write(DataOutputStream o)throws IOException;}
    private interface Reader<T>{T read(DataInputStream i)throws IOException;}
    private static byte[] write(Writer w){try{var b=new ByteArrayOutputStream();w.write(new DataOutputStream(b));return b.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
    private static <T>T read(byte[] b,Reader<T> r){if(b==null||b.length>1024)throw new IllegalArgumentException("Invalid control size");try{var i=new DataInputStream(new ByteArrayInputStream(b));T v=r.read(i);if(i.available()!=0)throw new IllegalArgumentException("Trailing control bytes");return v;}catch(IOException e){throw new IllegalArgumentException("Truncated control",e);}}
}
