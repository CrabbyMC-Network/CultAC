package ac.cult.cultac.protocol;

/** A requested value has no representation on the selected protocol. */
public final class UnsupportedOnVersionException extends IllegalArgumentException {
    public UnsupportedOnVersionException(String message) {
        super(message);
    }
}
