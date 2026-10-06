package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.bedrock.prediction.BedrockPredictionTrigger;
import ac.cult.cultac.bedrock.prediction.integration.BedrockFrameProcessor;
import ac.cult.cultac.bedrock.protocol.BedrockAuthInputFrame;
import ac.cult.cultac.bridge.wire.AuthInputMessage;
import ac.cult.cultac.bridge.wire.BridgeControlMessage;
import ac.cult.cultac.player.CultPlayer;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import java.util.ArrayList;

/** No alternate movement model: original proxy input goes through the normal Bedrock engine. */
public final class ProxyBridgeInputProcessor {
    private ProxyBridgeInputProcessor() { }
    public static BridgeControlMessage.InputResult process(CultPlayer player,long requestSequence,AuthInputMessage input) {
        if(!player.user.getPacketExecutor().inEventLoop()) throw new IllegalStateException("Proxy input outside packet owner");
        var previous=player.bedrockState.getLastOfferedFrame();
        Vec3 position=BedrockAuthInputFrames.physicalFeet(input);
        boolean teleport=flag(input,PlayerAuthInputData.HANDLE_TELEPORT);
        Vec3 delta=previous==null || !java.util.Objects.equals(previous.getPredictedVehicleId(),input.vehicleRuntimeId()) || teleport
                ? Vec3.ZERO : position.subtract(previous.getCoordinateFrame().toLocal(previous.getPosition()));
        BedrockAuthInputFrame frame=BedrockAuthInputFrames.fromWire(player.playerUUID,input,delta);
        var vehicle=player.compensatedEntities.getSelf().getRiding();
        if(frame.getPredictedVehicleJavaId()!=null && vehicle!=null && vehicle.bedrockRuntimeId==input.vehicleRuntimeId() && vehicle.getEntityId()==frame.getPredictedVehicleJavaId()) {
            player.bedrockState.movementCorrections.beginFrame(player,input.tick(),frame.getPredictedVehicleJavaId(),input.vehicleRuntimeId());
        }
        long beforeActions=player.bedrockState.authoritativeInputTick();
        var state=BedrockFrameProcessor.process(player,frame,BedrockPredictionTrigger.BEDROCK_THREAD,()->{},resolved->{
            if(!input.blockActions().isEmpty()) {
                var actions=new ArrayList<PlayerBlockActionData>();
                for(var a:input.blockActions()) {
                    if(a.action()<0 || a.action()>=PlayerActionType.values().length) throw new IllegalArgumentException("Unknown block action");
                    var action=new PlayerBlockActionData();action.setAction(PlayerActionType.values()[a.action()]);
                    action.setBlockPosition(Vector3i.from(a.x(),a.y(),a.z()));action.setFace(a.face());actions.add(action);
                }
                player.bedrockState.blockBreakActions.apply(player,resolved.getCoordinateFrame(),actions);
            }
        });
        if(state==null || player.getSetbackTeleportUtil().blocksBedrockTranslatedMovement() || player.compensatedEntities.getSelf().isDead
                || state.isVehicle() && (vehicle==null || frame.getPredictedVehicleJavaId()==null || vehicle.bedrockRuntimeId!=input.vehicleRuntimeId() || vehicle.getEntityId()!=frame.getPredictedVehicleJavaId())) {
            player.packetStateData.bedrockTranslatedMovement.reject();
            return new BridgeControlMessage.InputResult(requestSequence,false,input.feet(),vector(Vec3.ZERO),false,false,false,-1,false,player.bedrockState.movementCorrections.generation(),input.tick(),player.bedrockState.authoritativeInputTick()!=beforeActions);
        }
        if(state.isVehicle()) player.packetStateData.bedrockTranslatedMovement.allowVehicle(frame.getPredictedVehicleJavaId());
        else player.packetStateData.bedrockTranslatedMovement.allowPlayer(state.movementGrounded());
        return new BridgeControlMessage.InputResult(requestSequence,true,
                new AuthInputMessage.Double3(state.physicalFeetPosition().x(),state.physicalFeetPosition().y(),state.physicalFeetPosition().z()),
                new AuthInputMessage.Double3(state.velocity().x(),state.velocity().y(),state.velocity().z()),
                state.collisionFlags().horizontalCollision(),state.collisionFlags().verticalCollision(),state.movementGrounded(),
                state.isVehicle()?frame.getPredictedVehicleJavaId():-1,state.sprinting(),player.bedrockState.movementCorrections.generation(),player.bedrockState.processedClientTick(),player.bedrockState.authoritativeInputTick()!=beforeActions);
    }
    private static boolean flag(AuthInputMessage input,PlayerAuthInputData flag){int bit=flag.ordinal();return bit<64?(input.flagsLow()&(1L<<bit))!=0:(input.flagsHigh()&(1L<<(bit-64)))!=0;}
    private static AuthInputMessage.Double3 vector(Vec3 v){return new AuthInputMessage.Double3(v.x,v.y,v.z);}
}


