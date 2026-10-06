package ac.cult.cultac.bedrock.bridge;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.bedrock.protocol.BedrockMovementCorrection;
import net.minecraft.world.phys.Vec3;
/** Select transport before resolving optional local Geyser implementation classes. */
public final class BedrockBridgeTransport {
    private BedrockBridgeTransport() { }
    public static boolean sendPlayerTeleport(User user,Vec3 position,float yaw,float pitch,boolean onGround,int transaction) {
        if(ProxyBridgeRuntime.hasTransport(user))return ProxyBridgeRuntime.sendPlayerTeleport(user,position,yaw,pitch,onGround,transaction);
        if(user.getBedrockBridgeConnection()==null)return false;
        return GeyserBedrockBridgeRuntime.sendPlayerTeleport(user,position,yaw,pitch,onGround,transaction);
    }
    public static boolean sendMovementCorrection(User user,BedrockMovementCorrection correction,Vec3 claimedPosition,int debugId) {
        if(ProxyBridgeRuntime.hasTransport(user))return ProxyBridgeRuntime.sendMovementCorrection(user,correction);
        if(user.getBedrockBridgeConnection()==null)return false;
        return GeyserBedrockBridgeRuntime.sendMovementCorrection(user,correction,claimedPosition,debugId);
    }
}
