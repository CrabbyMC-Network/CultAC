package ac.cult.cultac.bedrock.bridge;
import ac.cult.cultac.bridge.wire.*;
import ac.cult.cultac.bedrock.prediction.integration.*;
import ac.cult.cultac.bedrock.prediction.model.PlayerDimensionsState;
import ac.cult.cultac.bedrock.prediction.state.BedrockMovementAttributeState;
import ac.cult.cultac.player.CultPlayer;
import java.util.*;
import net.minecraft.world.phys.Vec3;
/** Native state is captured on write, then applied only at its real client receipt. */
final class ProxyBridgeActorState {
    static Runnable capture(CultPlayer p,ActorStateMessage message,CultPlayer.BedrockTransaction before,CultPlayer.BedrockTransaction receipt) {
        return capture(p,message,before,receipt,()->p.bedrockState.lastMovementTick());
    }
    static Runnable capture(CultPlayer p,ActorStateMessage message,CultPlayer.BedrockTransaction before,CultPlayer.BedrockTransaction receipt,java.util.function.LongSupplier acknowledgedAfterTick) {
        boolean self=message.actorJavaId()==p.entityID || Objects.equals(p.bedrockState.observedActorRuntimeId(),message.actorRuntimeId());
        long generation=p.bedrockState.movementCorrections.generation();
        if(message.kind()==ActorStateMessage.Kind.ACTOR_CREATION) {
            if(self)return ()->p.checkManager.getSimulationProcessor().handleBedrockActorCreation(message.actorRuntimeId());
            return atActorReceipt(p,message,()->{
                var entity=p.compensatedEntities.getEntity(message.actorJavaId());
                if(entity!=null)entity.bedrockRuntimeId=message.actorRuntimeId();
            },false);
        }
        return switch(message.kind()) {
            case BLOCK_UPDATES -> ProxyBridgeBlockUpdates.capture(p,message.state());
            case BATCH -> {
                var bundle=ActorStateMessage.Bundle.decode(message.state());var callbacks=new ArrayList<Runnable>();
                for(var part:bundle.parts())callbacks.add(capture(p,new ActorStateMessage(message.request(),message.actorRuntimeId(),message.actorJavaId(),message.tick(),part.kind(),part.state()),before,receipt,acknowledgedAfterTick));
                yield ()->callbacks.forEach(Runnable::run);
            }
            case MOTION -> {
                var motion=ActorStateMessage.Vector.decode(message.state()).value();
                Vec3 vector=new Vec3(motion.x(),motion.y(),motion.z());
                if(self){p.checkManager.getKnockbackHandler().handleObservedEntityVelocity(vector,p.entityID,p.getSetbackTeleportUtil().getBedrockTeleportRevision(),before,receipt);yield ()->{};}
                yield atActorReceipt(p,message,()->{
                    var entity=p.compensatedEntities.getEntity(message.actorJavaId());
                    if(!p.compensatedEntities.vehicles.applyClientboundVehicleVelocity(entity,vector))entity.deltaMovement=vector;
                },true);
            }
            case METADATA -> {
                var m=ActorStateMessage.Metadata.decode(message.state());
                Runnable seatReceipt=()->{};
                if(m.seat()!=null){
                    var seat=m.seat();var offset=seat.offset();
                    var value=new GeyserBoatMetadata(null,null,null,null,null,null,null,null,new ac.cult.cultac.bedrock.prediction.geometry.Vec3d(offset.x(),offset.y(),offset.z()));
                    var seatMessage=new ActorStateMessage(message.request(),seat.vehicleRuntimeId(),seat.vehicleJavaId(),message.tick(),ActorStateMessage.Kind.BOAT_METADATA,new byte[0]);
                    seatReceipt=atActorReceipt(p,seatMessage,()->{
                        var boat=p.compensatedEntities.getEntity(seat.vehicleJavaId());if(boat.bedrockBoat==null||boat.bedrockBoat.runtimeId()!=seat.vehicleRuntimeId())return;
                        p.bedrockState.movementCorrections.queueUpdate(generation,seat.vehicleJavaId(),seat.vehicleRuntimeId(),message.tick(),message.tick()!=0,value.replayable());
                        boat.bedrockBoat=value.apply(boat.bedrockBoat,seat.vehicleRuntimeId());
                    },true);
                }
                Runnable appliedSeat=seatReceipt;
                yield ()->{
                    if(!self)return;
                    appliedSeat.run();
                    if(m.collisionWidth()!=null)p.bedrockState.confirmCollisionDefinition(new PlayerDimensionsState(m.collisionWidth(),m.collisionHeight()));
                    var metadata=p.bedrockState.movementCorrections.metadata(generation,message.tick(),new BedrockReplayEvent.Metadata(m.width(),m.height(),m.gliding(),m.crawling(),m.swimming(),m.spinning(),m.sprinting()),m.flagWords());
                    if(metadata!=null)p.checkManager.getSimulationProcessor().applyAcknowledgedBedrockMetadata(metadata.width(),metadata.height(),metadata.gliding(),metadata.crawling(),metadata.swimming(),m.sneaking(),metadata.spinning(),m.sleeping(),m.usingItem(),metadata.sprinting());
                    else if(m.sleeping()!=null)p.checkManager.getSimulationProcessor().handleBedrockSleepingStateChange(m.sleeping());
                    if(m.usingItem()!=null)p.bedrockState.movementCorrections.queueUpdate(generation,-1,message.actorRuntimeId(),0,false,new BedrockReplayContextEvent(Map.of(),null,null,-1,null,m.usingItem()));
                };
            }
            case ATTRIBUTES -> {
                var observed=ActorStateMessage.Attributes.decode(message.state());var values=new LinkedHashMap<String,BedrockMovementAttributeState>();
                for(var a:observed.values())values.put(a.name(),new BedrockMovementAttributeState(a.current(),a.min(),a.max(),a.defaultMin(),a.defaultMax(),a.defaultValue(),a.modifiers().stream().map(m->new BedrockMovementAttributeState.Modifier(m.id(),m.name(),m.amount(),m.operation(),m.operand(),m.serializable())).toList()));
                var attributes=new BedrockReplayAttributeEvent(values,message.tick()!=0);
                yield atActorReceipt(p,message,()->{
                    var entity=self?p.compensatedEntities.getSelf():p.compensatedEntities.getEntity(message.actorJavaId());
                    entity.bedrockAttributes=entity.bedrockAttributes.replace(values);p.bedrockState.movementCorrections.attributes(p,entity,message.tick(),attributes);
                },true);
            }
            case EFFECT -> {
                var e=ActorStateMessage.Effect.decode(message.state());var event=new BedrockReplayContextEvent(Map.of(),e.id(),e.level(),e.duration(),null,null);
                yield ()->p.bedrockState.movementCorrections.queueUpdate(generation,self?-1:message.actorJavaId(),message.actorRuntimeId(),message.tick(),message.tick()!=0,event);
            }
            case GAMEMODE -> {
                var g=ActorStateMessage.GameMode.decode(message.state());var event=new BedrockReplayContextEvent(Map.of(),null,null,-1,g.value(),null);
                yield ()->p.bedrockState.movementCorrections.queueUpdate(generation,self?-1:message.actorJavaId(),message.actorRuntimeId(),message.tick(),message.tick()!=0,event);
            }
            case COLLISION -> {
                var c=ActorStateMessage.Collision.decode(message.state());var dimensions=new PlayerDimensionsState(c.width(),c.height());
                yield ()->{if(self)p.bedrockState.confirmCollisionDefinition(dimensions);};
            }
            case VEHICLE_MOUNT -> {
                if(message.state().length!=0)throw new IllegalArgumentException("Unexpected mount body");
                yield p.getSetbackTeleportUtil().captureBedrockVehicleMount();
            }
            case GLIDE_BOOST -> {
                var boost=ActorStateMessage.GlideBoost.decode(message.state());
                var replay=new BedrockReplayEvent.Boost(boost.duration());
                yield ()->{
                    if(!self)return;
                    p.bedrockState.movementCorrections.queueUpdate(generation,-1,message.actorRuntimeId(),message.tick(),true,replay);
                    p.bedrockState.movementEffects.setGlideBoost(boost.duration(),acknowledgedAfterTick.getAsLong());
                };
            }
            case HORSE_METADATA -> {
                var m=ActorStateMessage.HorseMetadata.decode(message.state());
                var event=new BedrockReplayEvent.HorseMetadata(m.standing(),m.width(),m.height());
                yield ()->p.bedrockState.movementCorrections.queueUpdate(generation,message.actorJavaId(),message.actorRuntimeId(),message.tick(),message.tick()!=0,event);
            }
            case BOAT_METADATA -> {
                var m=ActorStateMessage.BoatMetadata.decode(message.state());
                var data=new org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap();
                if(m.width()!=null)data.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.WIDTH,m.width());
                if(m.height()!=null)data.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.HEIGHT,m.height());
                if(m.buoyant()!=null)data.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.IS_BUOYANT,m.buoyant());
                if(m.buoyancyJson()!=null)data.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.BUOYANCY_DATA,m.buoyancyJson());
                if(m.outOfControl()!=null){var flags=new EnumMap<org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag,Boolean>(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.class);flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.OUT_OF_CONTROL,m.outOfControl());flags.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag.LEASHED,m.leashed());data.putFlags(flags);}
                var seat=m.seat();if(seat!=null)data.put(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.SEAT_OFFSET,org.cloudburstmc.math.vector.Vector3f.from(seat.x(),seat.y(),seat.z()));
                var captured=GeyserBoatMetadata.capture(data);
                yield atActorReceipt(p,message,()->{
                    var boat=p.compensatedEntities.getEntity(message.actorJavaId());if(!boat.isBoat())return;
                    boat.bedrockBoat=captured.apply(m.creation()==null?boat.bedrockBoat:null,message.actorRuntimeId());
                    if(m.creation()!=null){
                        var spawn=m.creation();var feet=spawn.feet();var motion=spawn.motion();
                        var velocity=motion==null?ac.cult.cultac.bedrock.prediction.geometry.Vec3d.ZERO:new ac.cult.cultac.bedrock.prediction.geometry.Vec3d(motion.x(),motion.y(),motion.z());
                        BedrockVehiclePredictionState.initializeBoat(boat,new ac.cult.cultac.bedrock.prediction.geometry.Vec3d(feet.x(),feet.y(),feet.z()),velocity,spawn.yaw(),p.getSetbackTeleportUtil().getActiveBedrockCoordinateFrame());
                        return;
                    }
                    p.bedrockState.movementCorrections.queueUpdate(generation,message.actorJavaId(),message.actorRuntimeId(),message.tick(),message.tick()!=0,captured.replayable());
                    p.bedrockState.movementCorrections.queueUpdate(generation,message.actorJavaId(),message.actorRuntimeId(),0,false,captured.immediate());
                },true);
            }
            case ENTITY_TRANSFORM -> {
                var transform=EntityTransformMessage.decode(message.state());
                yield atActorReceipt(p,message,()->{
                    var entity=p.compensatedEntities.getEntity(message.actorJavaId());
                    if(transform.remove()){p.compensatedEntities.removeEntity(message.actorJavaId());return;}
                    if(transform.spawn())entity.bedrockRuntimeId=message.actorRuntimeId();
                    var motion=transform.motion();if(motion!=null)entity.deltaMovement=new Vec3(motion.x(),motion.y(),motion.z());
                    var raw=transform.position();var pos=new Vec3(raw.x(),raw.y(),raw.z());
                    var actual=transform.feet();var feet=new Vec3(actual.x(),actual.y(),actual.z());
                    if(entity==BedrockVehicleControl.controlledVehicle(p)&&!transform.spawn()&&!transform.forceLocal())return;
                    if(!transform.spawn()&&(transform.teleport()||transform.forceLocal())&&entity==p.compensatedEntities.getSelf().getRiding())p.checkManager.getListener(ac.cult.cultac.checks.impl.bedrock.BedrockMovement.class).onVehicleTeleport();
                    entity.onGround=transform.grounded();
                    if(transform.teleport()||transform.forceLocal()||!entity.isLivingEntity()&&!entity.isBoat()){
                        entity.bedrockInterpolation=null;entity.oldPacketLocation=null;
                        entity.setPositionRaw(ac.cult.cultac.utils.nmsutil.GetBoundingBox.getPacketEntityBoundingBox(p,feet.x,feet.y,feet.z,entity),transform.yaw()-(entity.isBoat()?90f:0f),transform.pitch());
                        if(transform.forceLocal()&&entity.bedrockPrediction!=null)BedrockVehiclePredictionState.rebase(entity,p.getSetbackTeleportUtil().getActiveBedrockCoordinateFrame());
                    }else{
                        var target=new BedrockEntityInterpolation.Target(pos,transform.yaw(),transform.pitch(),transform.offset(),transform.forceCompletion());
                        if(entity.bedrockInterpolation==null)entity.bedrockInterpolation=new BedrockEntityInterpolation(target);else entity.bedrockInterpolation.update(target);
                    }
                },!transform.spawn());
            }
            case CORRECTION -> {
                var c=CorrectionMessage.decode(message.state());
                var correction=new ac.cult.cultac.bedrock.protocol.BedrockMovementCorrection(c.sequence(),c.generation(),c.vehicleJavaId(),c.runtimeId(),c.tick(),new Vec3(c.feet().x(),c.feet().y(),c.feet().z()),new Vec3(c.velocity().x(),c.velocity().y(),c.velocity().z()),c.yaw(),c.pitch(),c.grounded(),new ac.cult.cultac.bedrock.protocol.BedrockCoordinateFrame(c.originX(),c.originZ(),c.originRevision()),c.teleportTransaction(),c.angularVelocity(),c.vehicle());
                p.bedrockState.movementCorrections.observe(p,correction);
                yield ()->p.bedrockState.movementCorrections.acknowledge(c.sequence());
            }
            case ACTOR_CREATION -> throw new IllegalStateException("Actor creation already handled");
        };
    }
    private static Runnable atActorReceipt(CultPlayer p,ActorStateMessage message,Runnable action,boolean requireMapping) {
        boolean self=message.actorJavaId()==p.entityID || Objects.equals(p.bedrockState.observedActorRuntimeId(),message.actorRuntimeId());
        var tracker=self?null:p.compensatedEntities.getTrackedEntity(message.actorJavaId());
        int creationTransaction=tracker==null?0:tracker.getLastTransactionHung();
        return ()->{
            if(self){if(Objects.equals(p.bedrockState.observedActorRuntimeId(),message.actorRuntimeId()))action.run();return;}
            if(tracker==null)return;
            p.latencyUtils.addRealTimeTask(creationTransaction,()->{
                if(p.compensatedEntities.getTrackedEntity(message.actorJavaId())!=tracker)return;
                var entity=p.compensatedEntities.getEntity(message.actorJavaId());
                if(entity!=null && (!requireMapping||entity.bedrockRuntimeId==message.actorRuntimeId()))action.run();
            });
        };
    }
}
