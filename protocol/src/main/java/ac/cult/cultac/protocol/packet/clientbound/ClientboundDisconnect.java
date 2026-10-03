package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.ByteArray;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Opaque network component; Cult only authors plain text or a translation key. */
public record ClientboundDisconnect(ByteArray reason) implements ClientboundPacket {
    public ClientboundDisconnect {
        Objects.requireNonNull(reason);
    }

    public static ClientboundDisconnect literal(String text) {
        return reason(text, false);
    }

    public static ClientboundDisconnect translatable(String key) {
        return reason(key, true);
    }

    private static ClientboundDisconnect reason(String text, boolean translated) {
        Objects.requireNonNull(text);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            // PacketEvents/ViaVersion use unnamed network NBT. Vanilla collapses
            // plain text to StringTag; an argument-free translation has one field.
            if (translated) {
                output.writeByte(10); // CompoundTag
                output.writeByte(8); // StringTag
                output.writeUTF("translate");
            } else output.writeByte(8);
            output.writeUTF(text); // NBT uses DataOutput's modified UTF-8.
            if (translated) output.writeByte(0);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot encode disconnect reason", failure);
        }
        return new ClientboundDisconnect(new ByteArray(bytes.toByteArray()));
    }
}
