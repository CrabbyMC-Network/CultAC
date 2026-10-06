package ac.cult.cultac.bridge.geyser;

import org.junit.Test;
import static org.junit.Assert.*;
import org.cloudburstmc.math.vector.Vector3f;

public class GatewayCoordinatesTest {
    @Test public void nativeEyeConversionMatchesExistingBridgeExactConstant() {
        var feet = GatewayCoordinates.playerFeet(Vector3f.from(.5F, 83.62001F, .5F));
        assertEquals((double) 83.62001F - 1.6200103759765625D, feet.y(), 0);
        assertEquals(.5D, feet.x(), 0);
    }
    @Test public void farmCoordinatesRetainExactNativeFloatBits() {
        var raw = Vector3f.from(10369.3828125F, -57.37999F, 9545.23828125F);
        var feet = GatewayCoordinates.playerFeet(raw);
        assertEquals(Double.doubleToLongBits((double) raw.getX()), Double.doubleToLongBits(feet.x()));
        assertEquals(Double.doubleToLongBits((double) raw.getZ()), Double.doubleToLongBits(feet.z()));
        assertEquals(Double.doubleToLongBits((double) raw.getY() - GatewayCoordinates.PLAYER_OFFSET), Double.doubleToLongBits(feet.y()));
    }
    @Test public void projectedEyeUsesFloatAdditionAsNativeGeyserDoes() {
        var feet = GatewayCoordinates.playerFeet(Vector3f.from(100F, 83.62001F, 123F));
        var eye = GatewayCoordinates.playerEye(feet);
        assertEquals((float) feet.y() + (float) GatewayCoordinates.PLAYER_OFFSET, eye.getY(), 0);
    }
}
