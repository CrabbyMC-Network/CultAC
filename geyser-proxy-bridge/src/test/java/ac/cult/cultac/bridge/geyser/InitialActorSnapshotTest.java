package ac.cult.cultac.bridge.geyser;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;

public class InitialActorSnapshotTest {
    private static InitialActorSnapshot snapshot() {
        var snapshot = new InitialActorSnapshot();
        var teleport = new MovePlayerPacket(); teleport.setRuntimeEntityId(42); teleport.setPosition(Vector3f.from(100, 82, 12));
        teleport.setRotation(Vector3f.ZERO); teleport.setMode(MovePlayerPacket.Mode.TELEPORT);
        snapshot.observe(teleport, 42, 100);
        return snapshot;
    }
    @Test public void fieldsMergeAcrossPartialMetadataWithoutLosingFlags() {
        var snapshot = snapshot();
        var flags = new SetEntityDataPacket(); flags.setRuntimeEntityId(42); flags.getMetadata().setFlag(EntityFlag.GLIDING, true);
        snapshot.observe(flags, 42, 100);
        var dimensions = new SetEntityDataPacket(); dimensions.setRuntimeEntityId(42); dimensions.getMetadata().put(EntityDataTypes.HEIGHT, .6F);
        snapshot.observe(dimensions, 42, 100);
        var replay = (SetEntityDataPacket) snapshot.replay(42, 100).get(1);
        assertTrue(replay.getMetadata().getFlag(EntityFlag.GLIDING));
        assertEquals(.6F, replay.getMetadata().get(EntityDataTypes.HEIGHT), 0);
        replay.getMetadata().setFlag(EntityFlag.GLIDING, false);
        assertTrue(((SetEntityDataPacket) snapshot.replay(42, 100).get(1)).getMetadata().getFlag(EntityFlag.GLIDING));
    }
    @Test public void completeAttributeMapSurvivesSeparatePartialPackets() {
        var snapshot = snapshot();
        var speed = new UpdateAttributesPacket(); speed.setRuntimeEntityId(42);
        speed.setAttributes(List.of(new AttributeData("minecraft:movement", 0, 1024, .2F, .2F)));
        snapshot.observe(speed, 42, 100);
        var health = new UpdateAttributesPacket(); health.setRuntimeEntityId(42);
        health.setAttributes(List.of(new AttributeData("minecraft:health", 0, 20, 20, 20)));
        snapshot.observe(health, 42, 100);
        assertEquals(2, ((UpdateAttributesPacket) snapshot.replay(42, 100).get(1)).getAttributes().size());
    }
    @Test public void effectsReplayRemainingNativeTicksAndRemoveDoesNotReappear() {
        var snapshot = snapshot();
        var effect = new MobEffectPacket(); effect.setRuntimeEntityId(42); effect.setEvent(MobEffectPacket.Event.ADD);
        effect.setEffectId(1); effect.setDuration(200); snapshot.observe(effect, 42, 100);
        assertEquals(150, ((MobEffectPacket) snapshot.replay(42, 150).get(1)).getDuration());
        effect.setEvent(MobEffectPacket.Event.REMOVE); snapshot.observe(effect, 42, 150);
        assertEquals(1, snapshot.replay(42, 150).size());
    }
    @Test(expected = IllegalStateException.class) public void transferClearsPreviousNativeTeleport() {
        var snapshot = snapshot(); snapshot.clear(); snapshot.replay(42, 100);
    }
    @Test public void nativeTeleportCloneIsPreservedAndIsolated() {
        var snapshot = snapshot(); var first = (MovePlayerPacket) snapshot.replay(42, 100).getFirst();
        assertEquals(MovePlayerPacket.Mode.TELEPORT, first.getMode());
        first.setPosition(Vector3f.ZERO);
        assertEquals(Vector3f.from(100, 82, 12), ((MovePlayerPacket) snapshot.replay(42, 100).getFirst()).getPosition());
    }
}
