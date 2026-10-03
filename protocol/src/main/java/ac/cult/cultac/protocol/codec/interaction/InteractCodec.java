/*
 * Read layout adapted from PacketEvents WrapperPlayClientInteractEntity,
 * revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.interaction;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.VariantCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundInteract;
import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.value.InteractAction;
import ac.cult.cultac.protocol.value.Vec3d;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.Optional;

public final class InteractCodec implements VariantCodec<ServerboundInteract> {
    // 26.1 splits attacks out of interact into their own packet.
    private static final List<String> VARIANTS = List.of("interact", "attack");
    private static final int ATTACK = 1;
    private static final InteractAction[] ACTIONS = InteractAction.values();

    @Override
    public List<String> variants() { return VARIANTS; }

    @Override
    public ServerboundInteract read(ByteBuf input, ProtocolContext context) {
        int entityId = Wire.readVarInt(input);
        if (context.variant() == ATTACK) {
            return new ServerboundInteract(entityId, InteractAction.ATTACK, Hand.MAIN_HAND, Optional.empty(), false);
        }
        if (context.version().atLeast(ProtocolVersion.V26_1)) {
            // The native hand idMapper uses ZERO for an out-of-range value.
            Hand hand = Wire.readVarInt(input) == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            return new ServerboundInteract(entityId, InteractAction.INTERACT_AT, hand,
                    Optional.of(Wire.readLpVec3(input)), input.readBoolean());
        }
        int actionId = Wire.readVarInt(input);
        if (actionId < 0 || actionId >= ACTIONS.length) throw new MalformedPacketException("Invalid interaction action " + actionId);
        InteractAction action = ACTIONS[actionId];
        Optional<Vec3d> target = action == InteractAction.INTERACT_AT
                ? Optional.of(new Vec3d(input.readFloat(), input.readFloat(), input.readFloat())) : Optional.empty();
        Hand hand = Hand.MAIN_HAND;
        if (action != InteractAction.ATTACK) {
            int handId = Wire.readVarInt(input);
            if (handId < 0 || handId > 1) throw new MalformedPacketException("Invalid interaction hand " + handId);
            hand = handId == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        return new ServerboundInteract(entityId, action, hand, target, input.readBoolean());
    }
}
