package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.MalformedPacketException;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundClientInformation;
import ac.cult.cultac.protocol.value.ChatVisibility;
import ac.cult.cultac.protocol.value.ClientInformation;
import ac.cult.cultac.protocol.value.MainHand;
import ac.cult.cultac.protocol.value.ParticleStatus;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

public final class ClientInformationCodec implements WritablePacketCodec<ServerboundClientInformation> {
    private static final ChatVisibility[] CHAT = ChatVisibility.values();
    private static final MainHand[] HAND = MainHand.values();
    private static final ParticleStatus[] PARTICLES = ParticleStatus.values();

    @Override
    public ServerboundClientInformation read(ByteBuf input, ProtocolContext context) {
        String language = Wire.readString(input, 16);
        int distance = input.readByte();
        int chat = Wire.readVarInt(input);
        boolean colors = input.readBoolean();
        int model = input.readUnsignedByte();
        int hand = Wire.readVarInt(input);
        boolean filtering = input.readBoolean();
        boolean listing = input.readBoolean();
        int particles = Wire.readVarInt(input);
        // 26.3 replaces readEnum with each enum's idMapper: WRAP for chat and
        // particles, ZERO for arm. Earlier supported packets reject bad ordinals.
        if (context.version().atLeast(ProtocolVersion.V26_3)) {
            chat = Math.floorMod(chat, CHAT.length);
            hand = hand >= 0 && hand < HAND.length ? hand : 0;
            particles = Math.floorMod(particles, PARTICLES.length);
        }
        return new ServerboundClientInformation(new ClientInformation(
                language,
                distance,
                checked(CHAT, chat),
                colors,
                model,
                checked(HAND, hand),
                filtering,
                listing,
                checked(PARTICLES, particles)));
    }

    @Override
    public void write(ByteBuf output, ProtocolContext context, ServerboundClientInformation packet) {
        ClientInformation info = packet.information();
        // Native writeUtf uses its default 32767 bound, despite the read-side 16.
        Wire.writeString(output, info.language(), 32767);
        output.writeByte(info.viewDistance());
        Wire.writeVarInt(output, info.chatVisibility().ordinal());
        output.writeBoolean(info.chatColors());
        output.writeByte(info.modelCustomisation());
        Wire.writeVarInt(output, info.mainHand().ordinal());
        output.writeBoolean(info.textFilteringEnabled());
        output.writeBoolean(info.allowsListing());
        Wire.writeVarInt(output, info.particleStatus().ordinal());
    }

    private static <T> T checked(T[] values, int id) {
        if (id < 0 || id >= values.length)
            throw new MalformedPacketException("Invalid client information enum ID " + id);
        return values[id];
    }
}
