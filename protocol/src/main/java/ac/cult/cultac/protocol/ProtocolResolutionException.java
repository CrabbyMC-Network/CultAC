package ac.cult.cultac.protocol;

/** Required version data or a codec is missing; initialization must fail. */
public final class ProtocolResolutionException extends RuntimeException {
    public ProtocolResolutionException(String message) {
        super(message);
    }

    public ProtocolResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
