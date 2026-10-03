package ac.cult.cultac.protocol;

import ac.cult.cultac.protocol.codec.OpaqueCodecs;
import ac.cult.cultac.protocol.packet.Opaque;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One direction's packet families, in declaration order. Each declaration registers itself, so the
 * declarations are the catalog. Names are unnamespaced vanilla report names; which of them exist on a
 * version is read from that version's {@link ac.cult.cultac.protocol.data.ProtocolData}, never declared here.
 */
public final class PacketCatalog {
    private final PacketDirection direction;
    private final List<PacketType<?>> types = new ArrayList<>();

    private PacketCatalog(PacketDirection direction) {
        this.direction = direction;
    }

    public static PacketCatalog serverbound() { return new PacketCatalog(PacketDirection.SERVERBOUND); }
    public static PacketCatalog clientbound() { return new PacketCatalog(PacketDirection.CLIENTBOUND); }

    /** Declarations carried in exactly these phases. */
    public Scope in(ConnectionPhase phase, ConnectionPhase... more) {
        return new Scope(EnumSet.of(phase, more), ProtocolVersion.values()[0]);
    }

    public List<PacketType<?>> types() { return List.copyOf(types); }

    public final class Scope {
        private final Set<ConnectionPhase> phases;
        private final ProtocolVersion since;

        private Scope(Set<ConnectionPhase> phases, ProtocolVersion since) {
            this.phases = Set.copyOf(phases);
            this.since = since;
        }

        /** Limits declarations to the versions their codecs are verified against. */
        public Scope since(ProtocolVersion version) { return new Scope(phases, version); }

        /** A family with one wire name, which also forms its key. */
        public <R> PacketType<R> add(String name, Class<R> record, PacketCodec<R> codec) {
            return add(name, name, record, codec);
        }

        /** A family whose routing key predates its current wire name. */
        public <R> PacketType<R> add(String key, String name, Class<R> record, PacketCodec<R> codec) {
            return register(key, List.of(name), record, codec);
        }

        /** A family vanilla renamed; each version carries at most one of the names, and the codec reads whichever it is. */
        public <R> PacketType<R> renamed(String key, Class<R> record, PacketCodec<R> codec, String... names) {
            return register(key, List.of(names), record, codec);
        }

        /** A family spread over several wire names, owned and told apart by its codec. */
        public <R> PacketType<R> variants(String key, Class<R> record, VariantCodec<R> codec) {
            return register(key, codec.variants(), record, codec);
        }

        /** Routed by identity; the payload must be empty. */
        public PacketType<Opaque> empty(String name) { return add(name, Opaque.class, OpaqueCodecs.EMPTY); }

        /** Routed by identity with an empty payload Cult also writes. */
        public PacketType<Opaque> writableEmpty(String name) { return add(name, Opaque.class, OpaqueCodecs.WRITABLE_EMPTY); }

        /** Routed by identity; the payload has no consumer and stays unread. */
        public PacketType<Opaque> ignored(String name) { return add(name, Opaque.class, OpaqueCodecs.IGNORED); }

        private <R> PacketType<R> register(String key, List<String> names, Class<R> record, PacketCodec<R> codec) {
            PacketType<R> type = new PacketType<>(direction.name().toLowerCase(Locale.ROOT) + "." + key, record, direction,
                    phases, names.stream().map(name -> "minecraft:" + name).toList(), since, codec);
            types.add(type);
            return type;
        }
    }
}
