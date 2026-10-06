package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.session.GeyserSession;

/** Final native vehicle definitions, matching the local Geyser bridge's metadata policy. */
final class GatewayVehicleMetadata {
    private final Map<Long, EntityDataMap> boats = new HashMap<>();
    ActorStateMessage capture(GeyserSession session, BedrockPacket packet, long request, boolean rewrite,
                              ActorStateMessage transform) {
        long runtime = packet instanceof AddEntityPacket add ? add.getRuntimeEntityId()
                : packet instanceof SetEntityDataPacket value ? value.getRuntimeEntityId() : -1;
        if (runtime < 0) return null;
        var entity = session.getEntityCache().getEntityByGeyserId(runtime); if (entity == null) return null;
        var data = packet instanceof AddEntityPacket add ? add.getMetadata() : ((SetEntityDataPacket) packet).getMetadata();
        long tick = packet instanceof SetEntityDataPacket value ? value.getTick() : 0;
        if (entity instanceof BoatEntity) {
            if (rewrite) rewriteBuoyancy(data);
            var retained = boats.computeIfAbsent(runtime, ignored -> new EntityDataMap());
            retained.putAll(data);
            if (data.containsKey(EntityDataTypes.FLAGS)) retained.putFlags(data.getFlags().clone());
            var seat = data.get(EntityDataTypes.SEAT_OFFSET);
            var body = new ActorStateMessage.BoatMetadata(data.get(EntityDataTypes.WIDTH), data.get(EntityDataTypes.HEIGHT),
                    data.get(EntityDataTypes.IS_BUOYANT), data.containsKey(EntityDataTypes.FLAGS) ? data.getFlag(EntityFlag.OUT_OF_CONTROL) : null,
                    data.containsKey(EntityDataTypes.FLAGS) ? data.getFlag(EntityFlag.LEASHED) : null,
                    data.get(EntityDataTypes.BUOYANCY_DATA), seat == null ? null : vec(seat),
                    transform == null ? null : EntityTransformMessage.decode(transform.state()));
            return new ActorStateMessage(request, runtime, entity.getEntityId(), tick, ActorStateMessage.Kind.BOAT_METADATA, body.encode());
        }
        if (packet instanceof SetEntityDataPacket && equine(entity)) {
            var body = new ActorStateMessage.HorseMetadata(data.containsKey(EntityDataTypes.FLAGS) ? data.getFlag(EntityFlag.STANDING) : null,
                    data.get(EntityDataTypes.WIDTH), data.get(EntityDataTypes.HEIGHT));
            return new ActorStateMessage(request, runtime, entity.getEntityId(), tick, ActorStateMessage.Kind.HORSE_METADATA, body.encode());
        }
        return null;
    }
    void prepareBinding(SetEntityDataPacket packet) {
        var retained = boats.get(packet.getRuntimeEntityId()); if (retained == null) return;
        packet.getMetadata().putAll(retained);
        if (retained.containsKey(EntityDataTypes.FLAGS)) packet.getMetadata().putFlags(retained.getFlags().clone());
        rewriteBuoyancy(packet.getMetadata());
    }
    static void rewriteBuoyancy(EntityDataMap data) {
        String json = data.get(EntityDataTypes.BUOYANCY_DATA); if (json == null) return;
        var object = JsonParser.parseString(json).getAsJsonObject();
        object.addProperty("movement_type", "none"); object.addProperty("simulate_waves", false);
        data.put(EntityDataTypes.BUOYANCY_DATA, object.toString());
    }
    private static boolean equine(org.geysermc.geyser.entity.type.Entity entity) {
        return entity instanceof org.geysermc.geyser.entity.type.living.animal.horse.HorseEntity
                || entity instanceof org.geysermc.geyser.entity.type.living.animal.horse.SkeletonHorseEntity
                || entity instanceof org.geysermc.geyser.entity.type.living.animal.horse.ZombieHorseEntity
                || entity instanceof org.geysermc.geyser.entity.type.living.animal.horse.ChestedHorseEntity
                && !(entity instanceof org.geysermc.geyser.entity.type.living.animal.horse.LlamaEntity);
    }
    private static AuthInputMessage.Float3 vec(Vector3f value) { return new AuthInputMessage.Float3(value.getX(), value.getY(), value.getZ()); }
    void clear() { boats.clear(); }
    void remove(long runtime) { boats.remove(runtime); }
}
