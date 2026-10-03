package ac.cult.cultac.protocol.value;

/** Union of player command actions in wire order before sneak commands were removed. */
public enum PlayerCommandAction {
    PRESS_SHIFT_KEY,
    RELEASE_SHIFT_KEY,
    STOP_SLEEPING,
    START_SPRINTING,
    STOP_SPRINTING,
    START_JUMPING_WITH_HORSE,
    STOP_JUMPING_WITH_HORSE,
    OPEN_INVENTORY,
    START_FLYING_WITH_ELYTRA
}
