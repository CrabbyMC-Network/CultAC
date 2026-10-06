package ac.cult.cultac.bridge.wire;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Only original movement/control data, captured before Geyser changes its projection. */
public record AuthInputMessage(int protocol, long tick, int inputMode, int playMode, int interactionMode,
                               Float3 position, Double3 feet, Float3 velocity, float yaw, float pitch, float headYaw,
                               Float2 motion, Float2 analog, Float2 rawMotion, long flagsLow, long flagsHigh,
                               long vehicleRuntimeId, int vehicleJavaId, Float2 vehicleRotation,
                               List<BlockAction> blockActions) {
    public record Float3(float x, float y, float z) {
        public Float3 { if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)) throw new IllegalArgumentException("Invalid vector"); }
    }
    public record Float2(float x, float y) {
        public Float2 { if (!Float.isFinite(x)||!Float.isFinite(y)) throw new IllegalArgumentException("Invalid vector"); }
    }
    public record Double3(double x, double y, double z) {
        public Double3 { if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("Invalid vector"); }
    }
    public record BlockAction(int action, int x, int y, int z, int face) { }
    public AuthInputMessage {
        if (protocol < 1 || tick < 0 || position == null || feet == null || velocity == null || motion == null
                || !Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(headYaw)
                || blockActions == null || blockActions.size() > 256) throw new IllegalArgumentException("Invalid auth input");
        blockActions = List.copyOf(blockActions);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
            o.writeInt(protocol);o.writeLong(tick);o.writeInt(inputMode);o.writeInt(playMode);o.writeInt(interactionMode);
            vector(o,position);o.writeDouble(feet.x);o.writeDouble(feet.y);o.writeDouble(feet.z);
            vector(o,velocity);o.writeFloat(yaw);o.writeFloat(pitch);o.writeFloat(headYaw);
            vector(o,motion);optional(o,analog);optional(o,rawMotion);o.writeLong(flagsLow);o.writeLong(flagsHigh);
            o.writeLong(vehicleRuntimeId);o.writeInt(vehicleJavaId);optional(o,vehicleRotation);
            o.writeInt(blockActions.size());for(var a:blockActions){o.writeInt(a.action);o.writeInt(a.x);o.writeInt(a.y);o.writeInt(a.z);o.writeInt(a.face);}
            return bytes.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    public static AuthInputMessage decode(byte[] bytes) {
        if(bytes==null||bytes.length>BridgeEnvelope.MAX_BODY_BYTES)throw new IllegalArgumentException("Invalid auth input size");
        try {
            DataInputStream i=new DataInputStream(new ByteArrayInputStream(bytes));
            int protocol=i.readInt();long tick=i.readLong();int input=i.readInt(),play=i.readInt(),interaction=i.readInt();
            Float3 position=vector3(i);Double3 feet=new Double3(i.readDouble(),i.readDouble(),i.readDouble());
            Float3 velocity=vector3(i);float yaw=i.readFloat(),pitch=i.readFloat(),head=i.readFloat();
            Float2 motion=vector2(i),analog=optional(i),raw=optional(i);long low=i.readLong(),high=i.readLong();
            long runtime=i.readLong();int javaId=i.readInt();Float2 rotation=optional(i);
            int count=i.readInt();if(count<0||count>256||count*20>i.available())throw new IllegalArgumentException("Invalid action count");
            List<BlockAction> actions=new ArrayList<>(count);for(int n=0;n<count;n++)actions.add(new BlockAction(i.readInt(),i.readInt(),i.readInt(),i.readInt(),i.readInt()));
            if(i.available()!=0)throw new IllegalArgumentException("Trailing auth input data");
            return new AuthInputMessage(protocol,tick,input,play,interaction,position,feet,velocity,yaw,pitch,head,motion,analog,raw,low,high,runtime,javaId,rotation,actions);
        }catch(IOException truncated){throw new IllegalArgumentException("Truncated auth input",truncated);}
    }
    private static void vector(DataOutputStream o,Float3 v)throws IOException{o.writeFloat(v.x);o.writeFloat(v.y);o.writeFloat(v.z);}
    private static void vector(DataOutputStream o,Float2 v)throws IOException{o.writeFloat(v.x);o.writeFloat(v.y);}
    private static void optional(DataOutputStream o,Float2 v)throws IOException{o.writeBoolean(v!=null);if(v!=null)vector(o,v);}
    private static Float3 vector3(DataInputStream i)throws IOException{return new Float3(i.readFloat(),i.readFloat(),i.readFloat());}
    private static Float2 vector2(DataInputStream i)throws IOException{return new Float2(i.readFloat(),i.readFloat());}
    private static Float2 optional(DataInputStream i)throws IOException{return i.readBoolean()?vector2(i):null;}
}
