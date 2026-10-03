package ac.cult.cultac.protocol.value;

import java.util.Objects;

/** Consumed spawn fields; the dimension type ID belongs to the server's dynamic registry. */
public record PlayerSpawnInfo(int dimensionTypeId, String dimension, GameMode gameMode) {
    public PlayerSpawnInfo {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(gameMode);
    }
}
