package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.AuthInputMessage;
import java.util.ArrayList;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.session.GeyserSession;

/** Capture before any Geyser translator rewrites raw input or updates actor caches. */
final class AuthInputCapture {
    private AuthInputCapture() { }
    static AuthInputMessage capture(GeyserSession session, PlayerAuthInputPacket input) {
        long low = 0, high = 0;
        for (var flag : input.getInputData()) {
            int ordinal = flag.ordinal();
            if (ordinal >= 128) throw new IllegalStateException("Unsupported Bedrock input flag");
            if (ordinal < 64) low |= 1L << ordinal; else high |= 1L << (ordinal - 64);
        }
        long vehicleRuntime = input.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)
                ? input.getPredictedVehicle() : -1L;
        var vehicle = vehicleRuntime == -1L ? null : session.getEntityCache().getEntityByGeyserId(vehicleRuntime);
        if (vehicleRuntime != -1L && vehicle == null)
            throw new IllegalStateException("Unknown predicted vehicle");
        Vector3f raw = input.getPosition();
        Vector3f vehicleFeet = vehicle instanceof BoatEntity ? raw.down(vehicle.getOffset()) : raw;
        var feet = vehicle == null ? GatewayCoordinates.playerFeet(raw)
                : new AuthInputMessage.Double3(vehicleFeet.getX(), vehicleFeet.getY(), vehicleFeet.getZ());
        var actions = new ArrayList<AuthInputMessage.BlockAction>();
        for (var action : input.getPlayerActions()) {
            var position = action.getBlockPosition();
            actions.add(new AuthInputMessage.BlockAction(action.getAction().ordinal(),
                    position == null ? 0 : position.getX(), position == null ? 0 : position.getY(),
                    position == null ? 0 : position.getZ(), action.getFace()));
        }
        return new AuthInputMessage(session.protocolVersion(), input.getTick(), input.getInputMode().ordinal(),
                input.getPlayMode().ordinal(), input.getInputInteractionModel().ordinal(), vec(raw), feet,
                vec(input.getDelta()), input.getRotation().getY(), input.getRotation().getX(), input.getRotation().getZ(),
                vec(input.getMotion()), optional(input.getAnalogMoveVector()), optional(input.getRawMoveVector()), low, high,
                vehicleRuntime, vehicle == null ? -1 : vehicle.getEntityId(), optional(input.getVehicleRotation()), actions);
    }
    private static double decimal(float value) { return Double.parseDouble(Float.toString(value)); }
    private static AuthInputMessage.Float3 vec(Vector3f value) { return new AuthInputMessage.Float3(value.getX(), value.getY(), value.getZ()); }
    private static AuthInputMessage.Float2 vec(Vector2f value) { return new AuthInputMessage.Float2(value.getX(), value.getY()); }
    private static AuthInputMessage.Float2 optional(Vector2f value) { return value == null ? null : vec(value); }
}
