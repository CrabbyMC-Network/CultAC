package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.bedrock.protocol.BedrockAuthInputFrame;
import ac.cult.cultac.bedrock.protocol.BedrockMoveVector;
import ac.cult.cultac.bridge.wire.AuthInputMessage;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import java.util.Set;
import java.util.UUID;

/** The same raw-input construction for local and authenticated proxy transports. */
public final class BedrockAuthInputFrames {
    private BedrockAuthInputFrames() { }
    public static BedrockAuthInputFrame fromWire(UUID uuid, AuthInputMessage message, Vec3 displacement) {
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setTick(message.tick());
        packet.setPosition(Vector3f.from(message.position().x(), message.position().y(), message.position().z()));
        packet.setDelta(Vector3f.from(message.velocity().x(), message.velocity().y(), message.velocity().z()));
        packet.setRotation(Vector3f.from(message.pitch(), message.yaw(), message.headYaw()));
        packet.setMotion(Vector2f.from(message.motion().x(), message.motion().y()));
        packet.setInputMode(mode(InputMode.values(), message.inputMode()));
        packet.setPlayMode(mode(ClientPlayMode.values(), message.playMode()));
        packet.setInputInteractionModel(mode(InputInteractionModel.values(), message.interactionMode()));
        packet.setPredictedVehicle(message.vehicleRuntimeId());
        if(message.vehicleRotation()!=null) packet.setVehicleRotation(Vector2f.from(message.vehicleRotation().x(), message.vehicleRotation().y()));
        for(PlayerAuthInputData flag:PlayerAuthInputData.values()) {
            int bit=flag.ordinal();
            if(bit<64 ? (message.flagsLow() & (1L<<bit))!=0 : bit<128 && (message.flagsHigh() & (1L<<(bit-64)))!=0) packet.getInputData().add(flag);
        }
        Vec3 feet=physicalFeet(message);
        return create(uuid,message.protocol(),packet,feet,displacement,message.vehicleJavaId()<0?null:message.vehicleJavaId());
    }
    static Vec3 physicalFeet(AuthInputMessage message) {
        boolean vehicle=false;
        int bit=PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE.ordinal();
        vehicle=bit<64?(message.flagsLow()&(1L<<bit))!=0:(message.flagsHigh()&(1L<<(bit-64)))!=0;
        if(vehicle)return new Vec3(message.feet().x(),message.feet().y(),message.feet().z());
        var raw=message.position();
        var feet=ac.cult.cultac.bedrock.prediction.geometry.BedrockPositionTranslator.packetPositionToPhysicalFeet(
                new ac.cult.cultac.bedrock.prediction.geometry.Vec3d(raw.x(),raw.y(),raw.z()));
        return new Vec3(feet.x(),feet.y(),feet.z());
    }
    private static <T> T mode(T[] values,int index) {
        if(index == -1) return null;
        if(index<0 || index>=values.length) throw new IllegalArgumentException("Unknown input mode");
        return values[index];
    }
    private static Vec3 rawPosition(Vector3f vector) { return vector == null ? null : new Vec3(vector.getX(),vector.getY(),vector.getZ()); }
    private static Vec3 toVec3(Vector3f vector) {
        return vector == null ? null : new Vec3(Double.parseDouble(Float.toString(vector.getX())), Double.parseDouble(Float.toString(vector.getY())), Double.parseDouble(Float.toString(vector.getZ())));
    }
    static BedrockAuthInputFrame create(
            UUID uuid,
            int protocolVersion,
            PlayerAuthInputPacket packet,
            Vec3 position,
            Vec3 delta,
            Integer predictedVehicleJavaId
    ) {
        Set<PlayerAuthInputData> inputData = packet.getInputData();
        var motion = packet.getMotion();
        BedrockMoveVector moveVector = motion != null && (Math.abs(motion.getX()) > 1.0E-6F || Math.abs(motion.getY()) > 1.0E-6F) ? new BedrockMoveVector(motion.getX(), motion.getY()) : BedrockMoveVector.ZERO;


        return BedrockAuthInputFrame.builder(uuid)
                .protocolVersion(protocolVersion)
                .clientTick(packet.getTick())
                .inputMode(packet.getInputMode() == null ? -1 : packet.getInputMode().ordinal())
                .playMode(packet.getPlayMode() == null ? -1 : packet.getPlayMode().ordinal())
                .interactionModel(packet.getInputInteractionModel() == null ? -1 : packet.getInputInteractionModel().ordinal())
                .position(position)
                .packetPosition(rawPosition(packet.getPosition()))
                .delta(delta)
                .reportedEndOfTickVelocity(toVec3(packet.getDelta()))
                .predictedVehicleId(inputData.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE) ? packet.getPredictedVehicle() : -1L)
                .predictedVehicleJavaId(predictedVehicleJavaId)
                .vehicleRotation(packet.getVehicleRotation() == null ? null
                        : new BedrockAuthInputFrame.VehicleRotation(packet.getVehicleRotation().getY(), packet.getVehicleRotation().getX()))
                .rotation(packet.getRotation().getY(), packet.getRotation().getX(), packet.getRotation().getY())
                .moveVector(moveVector.x(), moveVector.z())
                .rawInputFlags(rawInputFlags(inputData))
                .rawInputFlagsHigh(rawInputFlagsHigh(inputData))
                .jumping(hasAnyInput(inputData, PlayerAuthInputData.JUMP_DOWN, PlayerAuthInputData.JUMPING, PlayerAuthInputData.START_JUMPING, PlayerAuthInputData.AUTO_JUMPING_IN_WATER))
                .jumpStarted(inputData.contains(PlayerAuthInputData.START_JUMPING))
                .jumpPressedRaw(inputData.contains(PlayerAuthInputData.JUMP_PRESSED_RAW))
                .jumpCurrentRaw(inputData.contains(PlayerAuthInputData.JUMP_CURRENT_RAW))
                .wantUp(inputData.contains(PlayerAuthInputData.WANT_UP))
                .sneaking(hasAnyInput(inputData, PlayerAuthInputData.SNEAK_CURRENT_RAW, PlayerAuthInputData.SNEAK_DOWN, PlayerAuthInputData.SNEAKING, PlayerAuthInputData.START_SNEAKING, PlayerAuthInputData.DESCEND, PlayerAuthInputData.SNEAK_TOGGLE_DOWN))
                .startSneaking(hasAnyInput(inputData, PlayerAuthInputData.START_SNEAKING, PlayerAuthInputData.SNEAK_PRESSED_RAW, PlayerAuthInputData.SNEAKING, PlayerAuthInputData.SNEAK_DOWN, PlayerAuthInputData.SNEAK_CURRENT_RAW))
                .stopSneaking(inputData.contains(PlayerAuthInputData.STOP_SNEAKING))
                .sprinting(inputData.contains(PlayerAuthInputData.SPRINTING))
                .startSwimming(inputData.contains(PlayerAuthInputData.START_SWIMMING))
                .stopSwimming(inputData.contains(PlayerAuthInputData.STOP_SWIMMING))
                .startCrawling(inputData.contains(PlayerAuthInputData.START_CRAWLING))
                .stopCrawling(inputData.contains(PlayerAuthInputData.STOP_CRAWLING))
                .startGliding(inputData.contains(PlayerAuthInputData.START_GLIDING))
                .stopGliding(inputData.contains(PlayerAuthInputData.STOP_GLIDING))
                .usingItem(hasAnyInput(inputData, PlayerAuthInputData.PERFORM_ITEM_INTERACTION, PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST, PlayerAuthInputData.START_USING_ITEM))
                .blockAction(inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS))
                .authorityMode("client-auth-input")
                .build();
    }

    private static boolean hasAnyInput(Set<PlayerAuthInputData> inputData, PlayerAuthInputData... inputs) {
        for (PlayerAuthInputData input : inputs) {
            if (inputData.contains(input)) {
                return true;
            }
        }
        return false;
    }

    private static long rawInputFlags(Set<PlayerAuthInputData> inputData) {
        long flags = 0L;
        for (PlayerAuthInputData input : inputData) {
            if (input.ordinal() < Long.SIZE) {
                flags |= 1L << input.ordinal();
            }
        }
        return flags;
    }

    private static long rawInputFlagsHigh(Set<PlayerAuthInputData> inputData) {
        long flags = 0L;
        for (PlayerAuthInputData input : inputData) {
            int ordinal = input.ordinal();
            if (ordinal >= Long.SIZE && ordinal < Long.SIZE * 2) {
                flags |= 1L << (ordinal - Long.SIZE);
            }
        }
        return flags;
    }

}
