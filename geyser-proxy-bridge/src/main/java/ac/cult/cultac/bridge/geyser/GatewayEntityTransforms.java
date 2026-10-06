package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import java.util.HashMap;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.session.GeyserSession;

/** Expands partial native transforms using only this actor's last ordered write. */
final class GatewayEntityTransforms {
    private record Position(int javaId, Vector3f raw, float offset, float yaw, float pitch) { }
    private final Map<Long, Position> written = new HashMap<>();
    ActorStateMessage capture(GeyserSession session, BedrockPacket packet, long request) {
        long id; Vector3f position; float yaw, pitch;
        boolean ground, teleport, spawn, force = false, complete = false, remove = false;
        Vector3f motion = null;
        if (packet instanceof AddEntityPacket value) {
            id = value.getRuntimeEntityId(); position = value.getPosition(); yaw = value.getRotation().getY();
            pitch = value.getRotation().getX(); ground = false; teleport = true; spawn = true; motion = value.getMotion();
        } else if (packet instanceof AddPlayerPacket value) {
            id = value.getRuntimeEntityId(); position = value.getPosition(); yaw = value.getRotation().getY();
            pitch = value.getRotation().getX(); ground = false; teleport = true; spawn = true; motion = value.getMotion();
        } else if (packet instanceof MoveEntityAbsolutePacket value) {
            id = value.getRuntimeEntityId(); position = value.getPosition(); yaw = angle(value.getRotation().getY());
            pitch = angle(value.getRotation().getX()); ground = value.isOnGround(); teleport = value.isTeleported(); spawn = false;
            force = value.isForceMove(); complete = value.isForceCompletion();
        } else if (packet instanceof MovePlayerPacket value) {
            id = value.getRuntimeEntityId(); position = value.getPosition(); yaw = value.getRotation().getY();
            pitch = value.getRotation().getX(); ground = value.isOnGround(); teleport = value.getMode() != MovePlayerPacket.Mode.NORMAL; spawn = false;
        } else if (packet instanceof MoveEntityDeltaPacket value) {
            id = value.getRuntimeEntityId(); Position previous = written.get(id); if (previous == null) return null;
            var flags = value.getFlags();
            position = Vector3f.from(flags.contains(MoveEntityDeltaPacket.Flag.HAS_X) ? value.getX() : previous.raw.getX(),
                    flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y) ? value.getY() : previous.raw.getY(),
                    flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z) ? value.getZ() : previous.raw.getZ());
            yaw = flags.contains(MoveEntityDeltaPacket.Flag.HAS_YAW) ? angle(value.getYaw()) : previous.yaw;
            pitch = flags.contains(MoveEntityDeltaPacket.Flag.HAS_PITCH) ? angle(value.getPitch()) : previous.pitch;
            var semantics = GatewayEntityDeltaFlags.read(value, session.protocolVersion());
            ground = semantics.onGround(); teleport = semantics.teleported(); force = semantics.forceLocal();
            complete = semantics.forceCompletion(); spawn = false;
        } else if (packet instanceof RemoveEntityPacket value) {
            id = value.getUniqueEntityId(); Position previous = written.remove(id); if (previous == null) return null;
            return message(request, id, previous.javaId, previous.raw, previous.offset, previous.yaw, previous.pitch,
                    false, false, false, false, false, true, null);
        } else return null;
        if (id == session.getPlayerEntity().geyserId()) return null;
        var entity = session.getEntityCache().getEntityByGeyserId(id); if (entity == null) return null;
        written.put(id, new Position(entity.getEntityId(), position, entity.getOffset(), yaw, pitch));
        return message(request, id, entity.getEntityId(), position, entity.getOffset(), yaw, pitch,
                ground, teleport, spawn, force, complete, remove, motion);
    }
    private static ActorStateMessage message(long request, long runtime, int javaId, Vector3f raw, float offset,
                                             float yaw, float pitch, boolean grounded, boolean teleport, boolean spawn,
                                             boolean force, boolean complete, boolean remove, Vector3f motion) {
        var state = new EntityTransformMessage(vec(raw), new AuthInputMessage.Double3(raw.getX(), raw.getY() - offset, raw.getZ()),
                offset, yaw, pitch, grounded, teleport, spawn, force, complete, remove, motion == null ? null : vec(motion));
        return new ActorStateMessage(request, runtime, javaId, 0, ActorStateMessage.Kind.ENTITY_TRANSFORM, state.encode());
    }
    private static float angle(float value) { return (byte) (value / (360.0F / 256.0F)) / 256.0F * 360.0F; }
    private static AuthInputMessage.Float3 vec(Vector3f value) { return new AuthInputMessage.Float3(value.getX(), value.getY(), value.getZ()); }
    void clear() { written.clear(); }
}
