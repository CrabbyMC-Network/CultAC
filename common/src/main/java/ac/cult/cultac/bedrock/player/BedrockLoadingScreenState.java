package ac.cult.cultac.bedrock.player;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.ServerboundLoadingScreenPacketType;

/**
 * Ordered loading transitions; only server-anticipated screens may stop actor movement.
 * Mojang's ServerboundLoadingScreenPacket documentation requires an anticipated start and matching ID:
 * https://github.com/Mojang/bedrock-protocol-docs/blob/main/json/ServerboundLoadingScreenPacket.json
 */
public final class BedrockLoadingScreenState {
    private final Set<Integer> anticipated = new HashSet<>();
    private boolean active;
    private Integer activeId;

    public void startGame() {
        anticipated.clear();
        anticipated.add(null);
        active = false;
        activeId = null;
    }

    public void changeDimension(Integer id) {
        if (id != null) anticipated.add(id);
    }

    public boolean accept(ServerboundLoadingScreenPacketType type, Integer id) {
        if (type == ServerboundLoadingScreenPacketType.START_LOADING_SCREEN) {
            if (!anticipated.remove(id)) return false;
            activeId = id;
            active = true;
            return true;
        }
        if (type == ServerboundLoadingScreenPacketType.END_LOADING_SCREEN
                && active && Objects.equals(activeId, id)) {
            active = false;
            activeId = null;
            return true;
        }
        return false;
    }

    public boolean active() { return active; }
}
