package ac.cult.cultac.protocol.packet;

import ac.cult.cultac.protocol.PacketType;

import java.util.List;
import java.util.stream.Stream;

/** The protocol module's whole catalog: {@link ServerboundPackets} then {@link ClientboundPackets}. */
public final class Packets {
    private static final List<PacketType<?>> ALL =
            Stream.concat(ServerboundPackets.all().stream(), ClientboundPackets.all().stream()).toList();

    private Packets() { }

    public static List<PacketType<?>> all() { return ALL; }
}
