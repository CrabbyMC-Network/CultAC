package ac.cult.cultac.protocol.codec.connection;

import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundCommandSuggestion;
import ac.cult.cultac.protocol.wire.Wire;
import io.netty.buffer.ByteBuf;

/** PacketEvents' tab-complete field order; only the text is consumed by Cult. */
public final class CommandSuggestionCodec implements PacketCodec<ServerboundCommandSuggestion> {
    @Override
    public ServerboundCommandSuggestion read(ByteBuf input, ProtocolContext context) {
        Wire.readVarInt(input); // Unused request ID precedes the command.
        return new ServerboundCommandSuggestion(Wire.readString(input, 32500));
    }
}
