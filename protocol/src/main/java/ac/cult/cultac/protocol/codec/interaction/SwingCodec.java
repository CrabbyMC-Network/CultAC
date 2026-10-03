/*
 * Layout adapted from PacketEvents WrapperPlayClientAnimation (1.9+ branch)
 * and WrapperPlayClientPunch, revision 5da85d7ad888f69732e0726aad8dde8f0e0ef4c1.
 * Copyright (C) 2022 retrooper and contributors; GPL-3.0-or-later.
 * https://github.com/retrooper/packetevents
 */
package ac.cult.cultac.protocol.codec.interaction;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.VariantCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundSwing;
import ac.cult.cultac.protocol.value.Hand;
import ac.cult.cultac.protocol.value.SwingKind;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class SwingCodec implements VariantCodec<ServerboundSwing> {
    // 26.3 replaces hand-bearing Swing with the empty, main-hand Punch packet.
    private static final List<String> VARIANTS = List.of("swing", "punch");
    private static final int PUNCH = 1;

    @Override
    public List<String> variants() { return VARIANTS; }

    @Override
    public ServerboundSwing read(ByteBuf input, ProtocolContext context) {
        if (context.variant() == PUNCH) return ServerboundSwing.PUNCH;
        int hand = Wire.readVarInt(input);
        // Vanilla uses strict readEnum here, including on 26.2.
        if (hand < 0 || hand > 1) throw new MalformedPacketException("Invalid swing hand " + hand);
        return new ServerboundSwing(hand == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, SwingKind.SWING);
    }
}
