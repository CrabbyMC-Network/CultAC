package ac.cult.cultac.bedrock.bridge;
import ac.cult.cultac.bridge.wire.*;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.data.TrackerData;
import ac.cult.cultac.utils.data.packetentity.PacketEntity;
import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.cult.cultac.bedrock.replay.offline.OfflineBedrockReplayRunnerTest;
import ac.cult.cultac.utils.nmsutil.EntityTypesCompat;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProxyBridgeActorStateTest {
    private static CultPlayer player()throws Exception{OfflineCultTestBootstrap.installConfig();var m=OfflineBedrockReplayRunnerTest.class.getDeclaredMethod("offlinePlayer");m.setAccessible(true);return(CultPlayer)m.invoke(null);}
    private static void close(CultPlayer p)throws Exception{var m=OfflineBedrockReplayRunnerTest.class.getDeclaredMethod("closeOfflinePlayer",CultPlayer.class);m.setAccessible(true);m.invoke(null,p);}
    private static ActorStateMessage creation(int id,long runtime){return new ActorStateMessage(1,runtime,id,0,ActorStateMessage.Kind.ACTOR_CREATION,new byte[0]);}
    @Test void nativeCreationWaitsForReceiptAndThenTheActualJavaSpawn()throws Exception{
        var p=player();try{int id=61;long runtime=901;var tracked=new TrackerData(0,0,0,0,0,EntityTypesCompat.PIG,5);p.compensatedEntities.serverPositionsMap.put(id,tracked);
            Runnable receipt=ProxyBridgeActorState.capture(p,creation(id,runtime),null,null);assertNull(p.compensatedEntities.getEntity(id));
            receipt.run();assertNull(p.compensatedEntities.getEntity(id));
            var entity=new PacketEntity(EntityTypesCompat.PIG,id);p.compensatedEntities.entityMap.put(id,entity);p.lastTransactionReceived.set(5);p.latencyUtils.handleNettySyncTransaction(5);
            assertEquals(runtime,entity.bedrockRuntimeId);
        }finally{close(p);}}
    @Test void reusedJavaEntityIdCannotReceiveTheOldNativeMapping()throws Exception{
        var p=player();try{int id=62;var old=new TrackerData(0,0,0,0,0,EntityTypesCompat.PIG,5);p.compensatedEntities.serverPositionsMap.put(id,old);
            Runnable receipt=ProxyBridgeActorState.capture(p,creation(id,902),null,null);receipt.run();
            p.compensatedEntities.serverPositionsMap.put(id,new TrackerData(0,0,0,0,0,EntityTypesCompat.PIG,6));var entity=new PacketEntity(EntityTypesCompat.PIG,id);p.compensatedEntities.entityMap.put(id,entity);p.lastTransactionReceived.set(6);p.latencyUtils.handleNettySyncTransaction(6);
            assertEquals(-1,entity.bedrockRuntimeId);
        }finally{close(p);}}
    @Test void attributesCapturedBeforeCreationAreResolvedAfterItsNativeReceipt()throws Exception{
        var p=player();try{int id=63;long runtime=903;p.compensatedEntities.serverPositionsMap.put(id,new TrackerData(0,0,0,0,0,EntityTypesCompat.PIG,5));
            var attrs=new ActorStateMessage.Attributes(List.of(new ActorStateMessage.Attribute("minecraft:movement",.21f,0,1024,0,1024,.21f,List.of())));
            Runnable create=ProxyBridgeActorState.capture(p,creation(id,runtime),null,null);
            Runnable apply=ProxyBridgeActorState.capture(p,new ActorStateMessage(2,runtime,id,0,ActorStateMessage.Kind.ATTRIBUTES,attrs.encode()),null,null);
            var entity=new PacketEntity(EntityTypesCompat.PIG,id);p.compensatedEntities.entityMap.put(id,entity);p.lastTransactionReceived.set(5);
            assertTrue(entity.bedrockAttributes.values().isEmpty());create.run();apply.run();assertEquals(.21f,entity.bedrockAttributes.values().get("minecraft:movement").current());
        }finally{close(p);}}
}
