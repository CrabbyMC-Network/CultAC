package ac.cult.cultac.bridge.geyser;

import java.lang.reflect.Method;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;

record GatewayEntityDeltaFlags(boolean onGround, boolean teleported, boolean forceLocal, boolean forceCompletion) {
    private static final int SEPARATE_FLAGS_PROTOCOL = 2168;
    private static final ClassValue<Method[]> ACCESSORS = new ClassValue<>() {
        @Override protected Method[] computeValue(Class<?> type) {
            try {
                return new Method[]{type.getMethod("isOnGround"), type.getMethod("isForceMove"),
                        type.getMethod("isForceMoveLocalEntity"), type.getMethod("isForceCompletion")};
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Negotiated delta codec requires separate movement flags", e);
            }
        }
    };

    static GatewayEntityDeltaFlags read(MoveEntityDeltaPacket packet, int protocol) {
        if (protocol < SEPARATE_FLAGS_PROTOCOL) {
            var flags = packet.getFlags();
            return new GatewayEntityDeltaFlags(flags.contains(MoveEntityDeltaPacket.Flag.ON_GROUND),
                    flags.contains(MoveEntityDeltaPacket.Flag.TELEPORTING),
                    flags.contains(MoveEntityDeltaPacket.Flag.FORCE_MOVE_LOCAL_ENTITY),
                    flags.contains(MoveEntityDeltaPacket.Flag.FORCE_COMPLETION));
        }
        Method[] accessors = ACCESSORS.get(packet.getClass());
        try {
            return new GatewayEntityDeltaFlags((boolean) accessors[0].invoke(packet), (boolean) accessors[1].invoke(packet),
                    (boolean) accessors[2].invoke(packet), (boolean) accessors[3].invoke(packet));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read negotiated delta movement flags", e);
        }
    }
}

