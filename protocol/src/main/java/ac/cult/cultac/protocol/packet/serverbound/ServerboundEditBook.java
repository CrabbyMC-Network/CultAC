package ac.cult.cultac.protocol.packet.serverbound;

import java.util.List;
import java.util.Optional;

public record ServerboundEditBook(int slot, List<String> pages, Optional<String> title) implements ServerboundPacket {
    public ServerboundEditBook {
        pages = List.copyOf(pages);
    }
}
