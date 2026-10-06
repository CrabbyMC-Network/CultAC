package ac.cult.cultac.bridge.geyser;

import java.util.*;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.packet.*;

/** Only final observed native values are retained; replaying them creates new receipt proofs. */
final class InitialActorSnapshot {
    private final EntityDataMap metadata = new EntityDataMap();
    private final Map<String, AttributeData> attributes = new LinkedHashMap<>();
    private final Map<Integer, Effect> effects = new LinkedHashMap<>();
    private record Effect(MobEffectPacket packet, long observedTick) { }
    private Integer gameMode;
    private MovePlayerPacket teleport;
    private long metadataTick, attributesTick;
    boolean hasTeleport() { return teleport != null; }

    void observe(BedrockPacket packet, long self, long inputTick) {
        if (packet instanceof StartGamePacket start) {
            clear(); gameMode = start.getPlayerGameType().ordinal();
        } else if (packet instanceof SetEntityDataPacket value && value.getRuntimeEntityId() == self) {
            metadata.putAll(value.getMetadata());
            if (value.getMetadata().containsKey(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.FLAGS))
                metadata.putFlags(value.getMetadata().getFlags().clone());
            metadataTick = value.getTick();
        } else if (packet instanceof UpdateAttributesPacket value && value.getRuntimeEntityId() == self) {
            value.getAttributes().forEach(a -> attributes.put(a.getName(), a)); attributesTick = value.getTick();
        } else if (packet instanceof MobEffectPacket value && value.getRuntimeEntityId() == self) {
            if (value.getEvent() == MobEffectPacket.Event.REMOVE) effects.remove(value.getEffectId());
            else effects.put(value.getEffectId(), new Effect(value.clone(), inputTick));
        } else if (packet instanceof SetPlayerGameTypePacket value) gameMode = value.getGamemode();
        else if (packet instanceof MovePlayerPacket value && value.getRuntimeEntityId() == self
                && (value.getMode() == MovePlayerPacket.Mode.TELEPORT || value.getMode() == MovePlayerPacket.Mode.RESPAWN))
            teleport = value.clone();
    }
    List<BedrockPacket> replay(long self, long inputTick) {
        var packets = new ArrayList<BedrockPacket>();
        if (teleport == null) throw new IllegalStateException("Initial native teleport was not observed");
        packets.add(teleport.clone());
        if (!metadata.isEmpty()) {
            var packet = new SetEntityDataPacket(); packet.setRuntimeEntityId(self); packet.setTick(metadataTick);
            packet.getMetadata().putAll(metadata);
            if (metadata.containsKey(org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes.FLAGS))
                packet.getMetadata().putFlags(metadata.getFlags().clone());
            packets.add(packet);
        }
        if (!attributes.isEmpty()) {
            var packet = new UpdateAttributesPacket(); packet.setRuntimeEntityId(self); packet.setTick(attributesTick);
            packet.setAttributes(List.copyOf(attributes.values())); packets.add(packet);
        }
        for (var effect : effects.values()) {
            long remaining = Math.max(0, (long) effect.packet.getDuration() - Math.max(0, inputTick - effect.observedTick));
            if (remaining == 0) continue;
            var packet = effect.packet.clone(); packet.setDuration((int) remaining); packets.add(packet);
        }
        if (gameMode != null) {
            var packet = new SetPlayerGameTypePacket(); packet.setGamemode(gameMode); packets.add(packet);
        }
        return packets;
    }
    void clear() { metadata.clear(); attributes.clear(); effects.clear(); gameMode = null; teleport = null; metadataTick = 0; attributesTick = 0; }
}
