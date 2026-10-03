package ac.cult.cultac.protocol.value;

/** Server-observed item-use properties consumed by the anticheat, independent of platform item objects. */
public record ItemUseState(boolean active, Hand hand, boolean canSprint, float speedMultiplier) {
    public static final ItemUseState NONE = new ItemUseState(false, Hand.MAIN_HAND, true, 1.0F);

    public ItemUseState {
        java.util.Objects.requireNonNull(hand);
    }
}
