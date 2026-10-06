package ac.cult.cultac.bedrock.bridge;
import ac.cult.cultac.bridge.wire.AuthInputMessage;
import ac.cult.cultac.bedrock.prediction.geometry.*;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BedrockAuthInputFramesTest {
    private static void assertParity(ac.cult.cultac.bedrock.protocol.BedrockAuthInputFrame expected, ac.cult.cultac.bedrock.protocol.BedrockAuthInputFrame actual) {
        try {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                assertEquals(field.get(expected), field.get(actual), field.getName());
            }
        } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    private static PlayerAuthInputPacket packet(){var p=new PlayerAuthInputPacket();p.setTick(15);p.setPosition(Vector3f.from(1079.215f,65.62001f,-49.133f));p.setDelta(Vector3f.from(.123456f,-.08f,0));p.setMotion(Vector2f.from(.5f,1));p.setRotation(Vector3f.from(10,35,35));p.setInputMode(InputMode.values()[0]);p.setPlayMode(ClientPlayMode.values()[0]);p.setInputInteractionModel(InputInteractionModel.values()[0]);return p;}
    private static AuthInputMessage wire(PlayerAuthInputPacket p,Vec3 feet,Integer javaId,List<AuthInputMessage.BlockAction> actions){long lo=0,hi=0;for(var f:p.getInputData()){if(f.ordinal()<64)lo|=1L<<f.ordinal();else hi|=1L<<(f.ordinal()-64);}var pos=p.getPosition();var vel=p.getDelta();var move=p.getMotion();return new AuthInputMessage(924,p.getTick(),p.getInputMode().ordinal(),p.getPlayMode().ordinal(),p.getInputInteractionModel().ordinal(),new AuthInputMessage.Float3(pos.getX(),pos.getY(),pos.getZ()),new AuthInputMessage.Double3(feet.x,feet.y,feet.z),new AuthInputMessage.Float3(vel.getX(),vel.getY(),vel.getZ()),p.getRotation().getY(),p.getRotation().getX(),p.getRotation().getZ(),new AuthInputMessage.Float2(move.getX(),move.getY()),null,null,lo,hi,p.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)?p.getPredictedVehicle():-1L,javaId==null?-1:javaId,p.getVehicleRotation()==null?null:new AuthInputMessage.Float2(p.getVehicleRotation().getX(),p.getVehicleRotation().getY()),actions);}
    @Test void ordinaryPlayerUsesExactlyTheLocalFloatFeetAndDisplacement(){var p=packet();var uuid=UUID.randomUUID();var raw=p.getPosition();var v=BedrockPositionTranslator.packetPositionToPhysicalFeet(new Vec3d(raw.getX(),raw.getY(),raw.getZ()));var feet=new Vec3(v.x(),v.y(),v.z());var displacement=new Vec3(.1,0,-.25);var local=BedrockAuthInputFrames.create(uuid,924,p,feet,displacement,null);var proxy=BedrockAuthInputFrames.fromWire(uuid,wire(p,feet,null,List.of()),displacement);assertParity(local,proxy);assertEquals(-1L,proxy.getPredictedVehicleId());assertEquals(displacement,proxy.getDelta());}
    @Test void boatRetainsNativeFeetVehicleIdAndRotation(){var p=packet();p.getInputData().add(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);p.setPredictedVehicle(79);p.setVehicleRotation(Vector2f.from(12,40));var uuid=UUID.randomUUID();var feet=new Vec3(1.25,63,7.75);var delta=new Vec3(.2,0,.05);assertParity(BedrockAuthInputFrames.create(uuid,924,p,feet,delta,31),BedrockAuthInputFrames.fromWire(uuid,wire(p,feet,31,List.of()),delta));}
    @Test void teleportAndHighInputWordSurviveUnchanged(){var p=packet();p.getInputData().add(PlayerAuthInputData.HANDLE_TELEPORT);var high=Arrays.stream(PlayerAuthInputData.values()).filter(f->f.ordinal()>=64).findFirst().orElseThrow();p.getInputData().add(high);var uuid=UUID.randomUUID();var message=wire(p,Vec3.ZERO,null,List.of());var feet=BedrockAuthInputFrames.physicalFeet(message);var local=BedrockAuthInputFrames.create(uuid,924,p,feet,Vec3.ZERO,null);var proxy=BedrockAuthInputFrames.fromWire(uuid,AuthInputMessage.decode(message.encode()),Vec3.ZERO);assertParity(local,proxy);assertTrue(proxy.hasRawInputFlag(high));assertTrue(proxy.hasRawInputFlag(PlayerAuthInputData.HANDLE_TELEPORT));}
    @Test void blockActionCoordinatesAreNotLostOrMappedToOrigin(){var p=packet();p.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);var a=new AuthInputMessage.BlockAction(PlayerActionType.START_BREAK.ordinal(),1079,64,-49,2);var source=wire(p,Vec3.ZERO,null,List.of(a));var decoded=AuthInputMessage.decode(source.encode());assertEquals(List.of(a),decoded.blockActions());assertTrue(BedrockAuthInputFrames.fromWire(UUID.randomUUID(),decoded,Vec3.ZERO).hasBlockAction());}
}
