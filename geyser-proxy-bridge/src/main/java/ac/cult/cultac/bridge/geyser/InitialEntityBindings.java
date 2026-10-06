package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.*;
import java.util.*;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;

/** Re-observes previously written actor lifetimes using harmless native metadata boundaries. */
final class InitialEntityBindings {
    private record Actor(int javaId, List<ActorStateMessage.Part> observed) { }
    private final Map<Long, Actor> actors = new LinkedHashMap<>();
    private boolean overflow;
    void observe(ActorStateMessage message) {
        if (message == null) return;
        if (!actors.containsKey(message.actorRuntimeId()) && actors.size() >= 1024) { overflow = true; return; }
        if (message.kind() == ActorStateMessage.Kind.ENTITY_TRANSFORM) {
            var transform = EntityTransformMessage.decode(message.state());
            if (transform.remove()) { actors.remove(message.actorRuntimeId()); return; }
            if (transform.spawn()) actors.put(message.actorRuntimeId(), new Actor(message.actorJavaId(), new ArrayList<>()));
        }
        if (message.kind() == ActorStateMessage.Kind.BOAT_METADATA
                && ActorStateMessage.BoatMetadata.decode(message.state()).creation() != null)
            actors.put(message.actorRuntimeId(), new Actor(message.actorJavaId(), new ArrayList<>()));
        var actor = actors.get(message.actorRuntimeId());
        if (actor == null || actor.javaId != message.actorJavaId()) return;
        if (actors.size() > 1024 || actor.observed.size() >= 256) { overflow = true; return; }
        // Keep the actual ordered history; synthesizing a spawn at the latest target would change interpolation.
        actor.observed.add(new ActorStateMessage.Part(message.kind(), message.state()));
    }
    Map<SetEntityDataPacket, ActorStateMessage> replay() {
        if (overflow) throw new IllegalStateException("Initial native actor history exceeded its bound");
        var packets = new LinkedHashMap<SetEntityDataPacket, ActorStateMessage>();
        for (var entry : actors.entrySet()) {
            var actor = entry.getValue();
            var parts = new ArrayList<ActorStateMessage.Part>();
            parts.add(new ActorStateMessage.Part(ActorStateMessage.Kind.ACTOR_CREATION, new byte[0]));
            parts.addAll(actor.observed);
            for (int offset = 0; offset < parts.size(); offset += 16) {
                var packet = new SetEntityDataPacket(); packet.setRuntimeEntityId(entry.getKey());
                var state = new ActorStateMessage(0, entry.getKey(), actor.javaId, 0, ActorStateMessage.Kind.BATCH,
                        new ActorStateMessage.Bundle(parts.subList(offset, Math.min(parts.size(), offset + 16))).encode());
                packets.put(packet, state);
            }
        }
        return packets;
    }
    void clear() { actors.clear(); overflow = false; }
}
