package ac.cult.cultac.bridge.wire;

import java.io.*;
import java.util.*;
/** Final native values observed at the gateway write boundary, never inferred defaults. */
public record ActorStateMessage(long request,long actorRuntimeId,int actorJavaId,long tick,Kind kind,byte[] state) {
    public enum Kind { METADATA,MOTION,ATTRIBUTES,EFFECT,GAMEMODE,COLLISION,ACTOR_CREATION,CORRECTION,ENTITY_TRANSFORM,BATCH,BOAT_METADATA,VEHICLE_MOUNT,GLIDE_BOOST,HORSE_METADATA,BLOCK_UPDATES }
    public record BoatMetadata(Float width,Float height,Boolean buoyant,Boolean outOfControl,Boolean leashed,String buoyancyJson,AuthInputMessage.Float3 seat,EntityTransformMessage creation) {
        public BoatMetadata(Float width,Float height,Boolean buoyant,Boolean outOfControl,Boolean leashed,String buoyancyJson,AuthInputMessage.Float3 seat){this(width,height,buoyant,outOfControl,leashed,buoyancyJson,seat,null);}
        public BoatMetadata {if(creation!=null&&(!creation.spawn()||creation.remove()))throw new IllegalArgumentException("Invalid boat creation transform");if(width!=null&&(!Float.isFinite(width)||width<=0)||height!=null&&(!Float.isFinite(height)||height<=0)||buoyancyJson!=null&&buoyancyJson.length()>8192||(outOfControl==null)!=(leashed==null))throw new IllegalArgumentException("Invalid boat metadata");}
        public byte[] encode(){return write(o->{opt(o,width);opt(o,height);opt(o,buoyant);opt(o,outOfControl);opt(o,leashed);o.writeBoolean(buoyancyJson!=null);if(buoyancyJson!=null)o.writeUTF(buoyancyJson);o.writeBoolean(seat!=null);if(seat!=null){o.writeFloat(seat.x());o.writeFloat(seat.y());o.writeFloat(seat.z());}o.writeBoolean(creation!=null);if(creation!=null){byte[] c=creation.encode();o.writeInt(c.length);o.write(c);}});}
        public static BoatMetadata decode(byte[] b){return read(b,i->new BoatMetadata(optFloat(i),optFloat(i),optBool(i),optBool(i),optBool(i),i.readBoolean()?i.readUTF():null,i.readBoolean()?new AuthInputMessage.Float3(i.readFloat(),i.readFloat(),i.readFloat()):null,i.readBoolean()?EntityTransformMessage.decode(i.readNBytes(count(i,256))):null));}
    }
    public record GlideBoost(int duration) {
        public GlideBoost {if(duration< -1)throw new IllegalArgumentException("Invalid glide boost duration");}
        public byte[] encode(){return write(o->o.writeInt(duration));}
        public static GlideBoost decode(byte[] b){return read(b,i->new GlideBoost(i.readInt()));}
    }
    public record HorseMetadata(Boolean standing,Float width,Float height) {
        public HorseMetadata {if(width!=null&&(!Float.isFinite(width)||width<=0)||height!=null&&(!Float.isFinite(height)||height<=0))throw new IllegalArgumentException("Invalid horse metadata");}
        public byte[] encode(){return write(o->{opt(o,standing);opt(o,width);opt(o,height);});}
        public static HorseMetadata decode(byte[] b){return read(b,i->new HorseMetadata(optBool(i),optFloat(i),optFloat(i)));}
    }
    public record Part(Kind kind,byte[] state) {
        public Part {if(kind==null||kind==Kind.BATCH||state==null||state.length>BridgeEnvelope.MAX_BODY_BYTES-40)throw new IllegalArgumentException("Invalid native state part");state=state.clone();}
        @Override public byte[] state(){return state.clone();}
    }
    public record Bundle(List<Part> parts) {
        public Bundle {parts=List.copyOf(parts);if(parts.isEmpty()||parts.size()>16)throw new IllegalArgumentException("Invalid native state batch");}
        public byte[] encode(){return write(o->{o.writeInt(parts.size());for(var p:parts){o.writeByte(p.kind.ordinal());o.writeInt(p.state.length);o.write(p.state);}});}
        public static Bundle decode(byte[] b){return read(b,i->{int count=count(i,16);var parts=new ArrayList<Part>();for(int n=0;n<count;n++){int k=i.readUnsignedByte();if(k>=Kind.values().length)throw new IllegalArgumentException("Invalid state kind");int length=count(i,BridgeEnvelope.MAX_BODY_BYTES);if(length>i.available())throw new IllegalArgumentException("Truncated state part");parts.add(new Part(Kind.values()[k],i.readNBytes(length)));}return new Bundle(parts);});}
    }
    public record Seat(int vehicleJavaId,long vehicleRuntimeId,AuthInputMessage.Float3 offset) {
        public Seat {if(vehicleJavaId<0||vehicleRuntimeId<0||offset==null)throw new IllegalArgumentException("Invalid native seat metadata");}
    }
    public record Metadata(Float width,Float height,Boolean gliding,Boolean crawling,Boolean swimming,Boolean sneaking,
                           Boolean spinning,Boolean sleeping,Boolean usingItem,Boolean sprinting,int flagWords,Float collisionWidth,Float collisionHeight,Seat seat) {
        public Metadata(Float width,Float height,Boolean gliding,Boolean crawling,Boolean swimming,Boolean sneaking,Boolean spinning,Boolean sleeping,Boolean usingItem,Boolean sprinting,int flagWords,Float collisionWidth,Float collisionHeight){this(width,height,gliding,crawling,swimming,sneaking,spinning,sleeping,usingItem,sprinting,flagWords,collisionWidth,collisionHeight,null);}
        public Metadata(Float width,Float height,Boolean gliding,Boolean crawling,Boolean swimming,Boolean sneaking,Boolean spinning,Boolean sleeping,Boolean usingItem,Boolean sprinting,int flagWords){this(width,height,gliding,crawling,swimming,sneaking,spinning,sleeping,usingItem,sprinting,flagWords,null,null,null);}
        public Metadata { if(collisionWidth!=null&&(!Float.isFinite(collisionWidth)||collisionWidth<=0)||collisionHeight!=null&&(!Float.isFinite(collisionHeight)||collisionHeight<=0)||(collisionWidth==null)!=(collisionHeight==null))throw new IllegalArgumentException("Invalid native collision definition"); if(width!=null&&(!Float.isFinite(width)||width<=0)||height!=null&&(!Float.isFinite(height)||height<=0)||(flagWords&~3)!=0)throw new IllegalArgumentException("Invalid native metadata"); }
        public byte[] encode(){return write(o->{opt(o,width);opt(o,height);for(Boolean v:new Boolean[]{gliding,crawling,swimming,sneaking,spinning,sleeping,usingItem,sprinting})opt(o,v);o.writeInt(flagWords);opt(o,collisionWidth);opt(o,collisionHeight);o.writeBoolean(seat!=null);if(seat!=null){o.writeInt(seat.vehicleJavaId);o.writeLong(seat.vehicleRuntimeId);o.writeFloat(seat.offset.x());o.writeFloat(seat.offset.y());o.writeFloat(seat.offset.z());}});}
        public static Metadata decode(byte[] b){return read(b,i->new Metadata(optFloat(i),optFloat(i),optBool(i),optBool(i),optBool(i),optBool(i),optBool(i),optBool(i),optBool(i),optBool(i),i.readInt(),optFloat(i),optFloat(i),i.readBoolean()?new Seat(i.readInt(),i.readLong(),new AuthInputMessage.Float3(i.readFloat(),i.readFloat(),i.readFloat())):null));}
    }
    public record Modifier(String id,String name,float amount,int operation,int operand,boolean serializable) {
        public Modifier { text(id);text(name);finite(amount);if(operation<0||operation>3||operand<0||operand>2)throw new IllegalArgumentException("Invalid modifier"); }
    }
    public record Attribute(String name,float current,float min,float max,float defaultMin,float defaultMax,float defaultValue,List<Modifier> modifiers) {
        public Attribute {text(name);finite(current);finite(min);finite(max);finite(defaultMin);finite(defaultMax);finite(defaultValue);if(min>max||defaultMin>defaultMax)throw new IllegalArgumentException("Invalid attribute range");modifiers=List.copyOf(modifiers);if(modifiers.size()>64)throw new IllegalArgumentException("Too many attribute modifiers");}
    }
    public record Attributes(List<Attribute> values) {
        public Attributes {values=List.copyOf(values);if(values.size()>64)throw new IllegalArgumentException("Too many attributes");}
        public byte[] encode(){return write(o->{o.writeInt(values.size());for(var a:values){o.writeUTF(a.name);o.writeFloat(a.current);o.writeFloat(a.min);o.writeFloat(a.max);o.writeFloat(a.defaultMin);o.writeFloat(a.defaultMax);o.writeFloat(a.defaultValue);o.writeInt(a.modifiers.size());for(var m:a.modifiers){o.writeUTF(m.id);o.writeUTF(m.name);o.writeFloat(m.amount);o.writeInt(m.operation);o.writeInt(m.operand);o.writeBoolean(m.serializable);}}});}
        public static Attributes decode(byte[] b){return read(b,i->{int n=count(i,64);var values=new ArrayList<Attribute>();for(int x=0;x<n;x++){String name=i.readUTF();float cur=i.readFloat(),min=i.readFloat(),max=i.readFloat(),dmin=i.readFloat(),dmax=i.readFloat(),dval=i.readFloat();int m=count(i,64);var mods=new ArrayList<Modifier>();for(int y=0;y<m;y++)mods.add(new Modifier(i.readUTF(),i.readUTF(),i.readFloat(),i.readInt(),i.readInt(),i.readBoolean()));values.add(new Attribute(name,cur,min,max,dmin,dmax,dval,mods));}return new Attributes(values);});}
    }
    public record Effect(int id,int level,int duration) {
        public Effect {if(id<0||level<0||duration< -1)throw new IllegalArgumentException("Invalid effect");}
        public byte[] encode(){return write(o->{o.writeInt(id);o.writeInt(level);o.writeInt(duration);});}
        public static Effect decode(byte[] b){return read(b,i->new Effect(i.readInt(),i.readInt(),i.readInt()));}
    }
    public record Vector(AuthInputMessage.Double3 value) {
        public byte[] encode(){return write(o->{o.writeDouble(value.x());o.writeDouble(value.y());o.writeDouble(value.z());});}
        public static Vector decode(byte[] b){return read(b,i->new Vector(new AuthInputMessage.Double3(i.readDouble(),i.readDouble(),i.readDouble())));}
    }
    public record Collision(float width,float height) {
        public Collision {finite(width);finite(height);if(width<=0||height<=0)throw new IllegalArgumentException("Invalid collision size");}
        public byte[] encode(){return write(o->{o.writeFloat(width);o.writeFloat(height);});}
        public static Collision decode(byte[] b){return read(b,i->new Collision(i.readFloat(),i.readFloat()));}
    }
    public record GameMode(int value) {
        public GameMode {if(value<0)throw new IllegalArgumentException("Invalid game mode");}
        public byte[] encode(){return write(o->o.writeInt(value));}
        public static GameMode decode(byte[] b){return read(b,i->new GameMode(i.readInt()));}
    }
    public ActorStateMessage {if(request<0||actorRuntimeId<0||tick<0||state==null||state.length>BridgeEnvelope.MAX_BODY_BYTES-40)throw new IllegalArgumentException("Invalid actor state");Objects.requireNonNull(kind);state=state.clone();}
    @Override public byte[] state(){return state.clone();}
    public byte[] encode(){return write(o->{o.writeLong(request);o.writeLong(actorRuntimeId);o.writeInt(actorJavaId);o.writeLong(tick);o.writeByte(kind.ordinal());o.writeInt(state.length);o.write(state);});}
    public static ActorStateMessage decode(byte[] b){return read(b,i->{long req=i.readLong(),id=i.readLong();int javaId=i.readInt();long tick=i.readLong();int kind=i.readUnsignedByte();if(kind>=Kind.values().length)throw new IllegalArgumentException("Unknown actor state");int n=count(i,BridgeEnvelope.MAX_BODY_BYTES);if(n!=i.available())throw new IllegalArgumentException("Invalid actor state length");return new ActorStateMessage(req,id,javaId,tick,Kind.values()[kind],i.readNBytes(n));});}
    private static void finite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite native attribute");}
    private static void text(String value){if(value==null||value.length()>1024)throw new IllegalArgumentException("Invalid native text");}
    private static int count(DataInputStream i,int max)throws IOException{int n=i.readInt();if(n<0||n>max)throw new IllegalArgumentException("Invalid count");return n;}
    private static void opt(DataOutputStream o,Float v)throws IOException{o.writeBoolean(v!=null);if(v!=null)o.writeFloat(v);}
    private static void opt(DataOutputStream o,Boolean v)throws IOException{o.writeByte(v==null?0:v?2:1);}
    private static Float optFloat(DataInputStream i)throws IOException{return i.readBoolean()?i.readFloat():null;}
    private static Boolean optBool(DataInputStream i)throws IOException{int n=i.readUnsignedByte();if(n>2)throw new IllegalArgumentException("Invalid boolean");return n==0?null:n==2;}
    private interface Writer{void write(DataOutputStream o)throws IOException;}
    private interface Reader<T>{T read(DataInputStream i)throws IOException;}
    private static byte[] write(Writer w){try{var b=new ByteArrayOutputStream();w.write(new DataOutputStream(b));return b.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
    private static <T>T read(byte[] b,Reader<T> r){if(b==null||b.length>BridgeEnvelope.MAX_BODY_BYTES)throw new IllegalArgumentException("Invalid state size");try{var i=new DataInputStream(new ByteArrayInputStream(b));T v=r.read(i);if(i.available()!=0)throw new IllegalArgumentException("Trailing state bytes");return v;}catch(IOException e){throw new IllegalArgumentException("Truncated state",e);}}
}
