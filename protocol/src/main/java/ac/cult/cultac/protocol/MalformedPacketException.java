package ac.cult.cultac.protocol;

/** A packet cannot be decoded according to its wire format. */
public final class MalformedPacketException extends RuntimeException {
    public MalformedPacketException(String message) {
        super(message);
    }

    public MalformedPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}
