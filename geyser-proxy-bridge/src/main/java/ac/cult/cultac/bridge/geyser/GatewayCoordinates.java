package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.AuthInputMessage;
import org.cloudburstmc.math.vector.Vector3f;

/** The same protocol eye offset and float conversion as the in-process native bridge. */
final class GatewayCoordinates {
    static final double PLAYER_OFFSET = 1.6200103759765625D;
    private GatewayCoordinates() { }
    static AuthInputMessage.Double3 playerFeet(Vector3f raw) {
        return new AuthInputMessage.Double3((double) raw.getX(), (double) raw.getY() - PLAYER_OFFSET, (double) raw.getZ());
    }
    static Vector3f playerEye(AuthInputMessage.Double3 feet) {
        return Vector3f.from(feet.x(), feet.y(), feet.z()).up((float) PLAYER_OFFSET);
    }
    static double decimal(float value) { return Double.parseDouble(Float.toString(value)); }
}
