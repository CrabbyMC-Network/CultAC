package ac.cult.cultac.protocol.value;

import ac.cult.cultac.protocol.MalformedPacketException;

public enum ConnectionIntent {
    STATUS,
    LOGIN,
    TRANSFER;

    public static ConnectionIntent fromWire(int value) {
        return switch (value) {
            case 1 -> STATUS;
            case 2 -> LOGIN;
            case 3 -> TRANSFER;
            default -> throw new MalformedPacketException("Unknown connection intent: " + value);
        };
    }
}
